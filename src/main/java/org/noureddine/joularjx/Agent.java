/*
 * Copyright (c) 2021-2022, Adel Noureddine, Université de Pays et des Pays de l'Adour.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the
 * GNU General Public License v3.0 only (GPL-3.0-only)
 * which accompanies this distribution, and is available at
 * https://www.gnu.org/licenses/gpl-3.0.en.html
 *
 * Author : Adel Noureddine
 */

package org.noureddine.joularjx;

import com.sun.management.OperatingSystemMXBean;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class Agent {

    /**
     * Global monitor used to implement mutual-exclusion. In the future this single
     * monitor may be broken up into many different monitors to reduce contention.
     */
    private static final Object LOCK = new Object();

    private static final Path PSYS = Path.of("/sys/class/powercap/intel-rapl/intel-rapl:1/energy_uj");
    private static final Path PKG = Path.of("/sys/class/powercap/intel-rapl/intel-rapl:0/energy_uj");
    private static final Path DRAM = Path.of("/sys/class/powercap/intel-rapl/intel-rapl:0/intel-rapl:0:2/energy_uj");
    private static final double MICROJOULES_IN_JOULES = 1000000;

    /**
     * Map to store total energy for each method
     */
    private final Map<String, Double> methodsEnergy;

    /**
     * Map to store total energy for filtered methods
     */
    private final Map<String, Double> methodsEnergyFiltered;

    /**
     * List of methods to filter for energy
     */
    private List<String> filterMethodNames;

    /**
     * Variables to collect the program energy consumption
     */
    private double totalProcessEnergy;

    /**
     * Size of list containing methods to filter for energy
     */
    private int sizeFilterMethodNames;

    /**
     * Sensor to use for monitor CPU energy/power consumption
     */
    private String energySensor;

    /**
     * Path for our power monitor program on Windows
     */
    private String powerMonitorPathWindows;

    /**
     * Process to run power monitor on Windows
     */
    private Process powerMonitorWindowsProcess;

    /**
     * JVM hook to statically load the java agent at startup.
     * After the Java Virtual Machine (JVM) has initialized, the premain method
     * will be called. Then the real application main method will be called.
     */
    public static void premain(String args, Instrumentation inst) {
        new Agent().run();
    }

    public Agent() {
        this.methodsEnergy = new HashMap<>();
        this.filterMethodNames = new ArrayList<>();
        this.methodsEnergyFiltered = new HashMap<>();
        this.totalProcessEnergy = 0;
        this.sizeFilterMethodNames = 0;
        this.energySensor = "";
        this.powerMonitorPathWindows = "";
        this.powerMonitorWindowsProcess = null;
    }

    /**
     * Check if methodName starts with one of the filtered method names
     * @param methodName Name of method
     * @return True if methodName starts with one of the filtered method names, false if not
     */
    private boolean isStartsFilterMethodNames(String methodName) {
        // In most cases, there will be one filtered method name
        // So we check that to gain performance and avoid looping the list
        if (sizeFilterMethodNames == 1) {
            return methodName.startsWith(filterMethodNames.get(0));
        } else {
            // Check for every filtered method name if methodName start with any of them
            for (String filterMethod : filterMethodNames) {
                if (methodName.startsWith(filterMethod)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Calculate process energy consumption
     * @param totalCPUUsage Total CPU usage
     * @param processCPUUSage Process CPU usage
     * @param CPUEnergy CPU energy
     * @return Process energy consumption
     */
    private double calculateProcessCPUEnergy(Double totalCPUUsage, Double processCPUUSage, Double CPUEnergy) {
        return (processCPUUSage * CPUEnergy) / totalCPUUsage;
    }

    public void run() {
        Thread.currentThread().setName("JalenX Agent Thread");
        System.out.println("+---------------------------------+");
        System.out.println("| JoularJX Agent Version 1.0      |");
        System.out.println("+---------------------------------+");

        ThreadMXBean mxbean = ManagementFactory.getThreadMXBean();
        // Check if CPU Time measurement is supported by the JVM. Quit otherwise
        if (!mxbean.isThreadCpuTimeSupported()) {
            System.out.println("Thread CPU Time is not supported on this Java Virtual Machine. Existing...");
            System.exit(1);
        }

        // Enable CPU Time measurement if it is disabled
        if (!mxbean.isThreadCpuTimeEnabled())
            mxbean.setThreadCpuTimeEnabled(true);

        // Get Process ID of current application
        Long appPid = ProcessHandle.current().pid();

        // Get OS
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();

        if (osName.contains("linux")) {
            // GNU/Linux
            if (osArch.contains("aarch64") || osArch.contains("arm")) {
                // Platform not supported
                System.out.println("Platform not supported. Existing...");
                System.exit(1);
            } else {
                // Suppose it's x86/64, check for powercap RAPL
                try {
                    String raplFolderPath = "/sys/class/powercap/intel-rapl/intel-rapl:0";
                    File raplFolder = new File(raplFolderPath);
                    if (raplFolder.exists()) {
                        // Rapl is supported
                        this.energySensor = "rapl";
                    } else {
                        // If no RAPL, then no support
                        System.out.println("Platform not supported. Existing...");
                        System.exit(1);
                    }
                } catch (Exception e) {
                    // If no RAPL, then no support
                    System.out.println("Platform not supported. Existing...");
                    System.exit(1);
                }
            }
        } else if (osName.contains("win")) {
            // Windows
            // Check for Intel Power Gadget, and PowerJoular Windows
            this.energySensor = "windows";
        } else {
            // Other platforms not supported
            System.out.println("Platform not supported. Existing...");
            System.exit(1);
        }

        // Read properties file
        Properties prop = new Properties();
        FileInputStream fis = null;
        try {
            fis = new FileInputStream("./config.properties");
            prop.load(fis);
        } catch (IOException e) {
            System.exit(1);
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Get filtered methods
        this.filterMethodNames = Arrays.asList(prop.getProperty("filter-method-names").split(","));
        this.sizeFilterMethodNames = filterMethodNames.size();
        this.powerMonitorPathWindows = prop.getProperty("powermonitor-path");

        // Get OS MxBean to collect CPU and Process loads
        OperatingSystemMXBean osMxBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        // Loop for a couple of seconds to initialize OSMXBean to get accurate details (first call will return -1)
        int i = 0;
        System.out.println("Please wait while initializing JoularJX...");
        while (i < 2) {
            osMxBean.getCpuLoad();
            osMxBean.getProcessCpuLoad();
            if (energySensor.equals("windows")) {
                // On windows, start power monitoring a few seconds to initialize
                try {
                    this.powerMonitorWindowsProcess = Runtime.getRuntime().exec(powerMonitorPathWindows);
                } catch (IOException ex) {
                    ex.printStackTrace();
                    System.out.println("Can't start power monitor on Windows. Existing...");
                    System.exit(1);
                }
            }
            i++;
            try {
                Thread.sleep(500);
            } catch (Exception ignoredException) {}
        }
        System.out.println("Initialization finished");

        /**
         * Thread to calculate at runtime the power consumption per thread following a determined cycle duration
         */
        new Thread() {
            public void run() {
                Thread.currentThread().setName("JalenX Agent Computation");
                System.out.println("Started monitoring application with ID " + appPid);

                // CPU time for each thread
                Map<Long, Long> threadsCPUTime = new HashMap<>();

                while (true) {
                    try {
                        Map<Long, Map<String, Integer>> methodsStats = new HashMap<>();
                        Map<Long, Map<String, Integer>> methodsStatsFiltered = new HashMap<>();
                        Set<Thread> threads = Thread.getAllStackTraces().keySet();

                        double energyBefore = 0.0;
                        switch (energySensor) {
                            case "rapl":
                                // Get CPU energy consumption with Intel RAPL
                                energyBefore = getRAPLEnergy();
                                break;
                            case "windows":
                                // Get CPU energy consumption on Windows using program monitor
                                // Nothing to do here, energy will be calculated after
                                break;
                            default:
                                break;
                        }

                        int duration = 0;
                        while (duration < 1000) {
                            for (Thread t : threads) {
                                long threadID = t.getId();
                                if (! methodsStats.containsKey(t.getId())) {
                                    methodsStats.put(threadID, new HashMap<>());
                                }

                                if (! methodsStatsFiltered.containsKey(t.getId())) {
                                    methodsStatsFiltered.put(threadID, new HashMap<>());
                                }
                                
                                // Only check runnable threads (not waiting or blocked)
                                if (t.getState() == State.RUNNABLE) {
                                    int onlyFirst = 0;
                                    int onlyFirstFiltered = 0;
                                    for (StackTraceElement ste : t.getStackTrace()) {
                                        String methName = ste.getClassName() + "." + ste.getMethodName();
                                        if (onlyFirst == 0) {
                                            synchronized (LOCK) {
                                                Map<String, Integer> methData = methodsStats.get(threadID);
                                                if (methData.containsKey(methName)) {
                                                    int methNumber = methData.get(methName) + 1;
                                                    methData.put(methName, methNumber);
                                                } else {
                                                    methData.put(methName, 1);
                                                }
                                            }
                                        }
                                        onlyFirst++;

                                        // Check filtered methods if in stacktrace
                                        if (isStartsFilterMethodNames(methName)) {
                                            if (onlyFirstFiltered == 0) {
                                                synchronized (LOCK) {
                                                    Map<String, Integer> methData = methodsStatsFiltered.get(threadID);
                                                    if (methData.containsKey(methName)) {
                                                        int methNumber = methData.get(methName) + 1;
                                                        methData.put(methName, methNumber);
                                                    } else {
                                                        methData.put(methName, 1);
                                                    }
                                                }
                                            }
                                            onlyFirstFiltered++;
                                        }
                                    }
                                }
                            }

                            duration += 10;
                            // Sleep for 10 ms
                            Thread.sleep(10);
                        }

                        double energyAfter = 0.0;
                        double CPUEnergy = 0.0;
                        double cpuLoad = osMxBean.getCpuLoad();
                        double processCpuLoad = osMxBean.getProcessCpuLoad();

                        switch (energySensor) {
                            case "rapl":
                                // At the end of the monitoring loop
                                energyAfter = getRAPLEnergy();
                                // Calculate total energy consumed in the monitoring loop
                                CPUEnergy = energyAfter - energyBefore;
                                break;
                            case "windows":
                                // Get CPU energy consumption on Windows using program monitor
                                try {
                                    BufferedReader input = new BufferedReader(new InputStreamReader(powerMonitorWindowsProcess.getInputStream()));
                                    String line = input.readLine();
                                    CPUEnergy = Double.parseDouble(line);
                                } catch (Exception ignoredException) {
                                    ignoredException.printStackTrace();
                                }
                                break;
                            default:
                                break;
                        }

                        // Calculate CPU energy consumption of the process of the JVM all its apps
                        double ProcessEnergy = calculateProcessCPUEnergy(cpuLoad, processCpuLoad, CPUEnergy);

                        // Adds current power to total energy
                        totalProcessEnergy += ProcessEnergy;

                        // Now we have:
                        // CPU energy for JVM process
                        // CPU energy for all processes
                        // We need to calculate energy for each thread
                        long totalThreadsCPUTime = 0;
                        for (Thread t : threads) {
                            long threadCPUTime = mxbean.getThreadCpuTime(t.getId());

                            // If thread already monitored, then calculate CPU time since last time
                            if (threadsCPUTime.containsKey(t.getId())) {
                                threadCPUTime -= threadsCPUTime.get(t.getId());
                            }

                            threadsCPUTime.put(t.getId(), threadCPUTime);
                            totalThreadsCPUTime += threadCPUTime;
                        }

                        Map<Long, Double> threadsPower = new HashMap<>();
                        for (Map.Entry<Long, Long> entry : threadsCPUTime.entrySet()) {
                            double percentageCPUTime = (entry.getValue() * 100.0) / totalThreadsCPUTime;
                            double threadPower = ProcessEnergy * (percentageCPUTime / 100.0);
                            threadsPower.put(entry.getKey(), threadPower);
                        }

                        // Now we have power for each thread, and stats for methods in each thread
                        // We allocated power for each method based on statistics
                        StringBuffer bufMeth = new StringBuffer();
                        for (Map.Entry<Long, Map<String, Integer>> entry : methodsStats.entrySet()) {
                            long threadID = entry.getKey();
                            for (Map.Entry<String, Integer> methEntry : entry.getValue().entrySet()) {
                                String methName = methEntry.getKey();
                                double methPower = threadsPower.get(threadID) * (methEntry.getValue() / 100.0);
                                if (methodsEnergy.containsKey(methEntry.getKey())) {
                                    // Add power (for 1 sec = energy) to total method energy
                                    double newMethEnergy = methodsEnergy.get(methName) + methPower;
                                    methodsEnergy.put(methName, newMethEnergy);
                                } else {
                                    methodsEnergy.put(methName, methPower);
                                }
                                bufMeth.append(methName + "," + methPower + "\n");
                            }
                        }

                        // For filtered methods
                        // Now we have power for each thread, and stats for methods in each thread
                        // We allocated power for each method based on statistics
                        StringBuffer bufMethFiltered = new StringBuffer();
                        for (Map.Entry<Long, Map<String, Integer>> entry : methodsStatsFiltered.entrySet()) {
                            long threadID = entry.getKey();
                            for (Map.Entry<String, Integer> methEntry : entry.getValue().entrySet()) {
                                String methName = methEntry.getKey();
                                double methPower = threadsPower.get(threadID) * (methEntry.getValue() / 100.0);
                                if (methodsEnergyFiltered.containsKey(methEntry.getKey())) {
                                    // Add power (for 1 sec = energy) to total method energy
                                    double newMethEnergy = methodsEnergyFiltered.get(methName) + methPower;
                                    methodsEnergyFiltered.put(methName, newMethEnergy);
                                } else {
                                    methodsEnergyFiltered.put(methName, methPower);
                                }
                                bufMethFiltered.append(methName + "," + methPower + "\n");
                            }
                        }

                        // Write to CSV file
                        String fileNameMethods = "joularJX-" + appPid + "-methods-power.csv";
                        try {
                            BufferedWriter out = new BufferedWriter(new FileWriter(fileNameMethods, false));
                            out.write(bufMeth.toString());
                            out.close();
                        } catch (Exception ignored) {}

                        // Write to CSV file for filtered methods
                        String fileNameMethodsFiltered = "joularJX-" + appPid + "-methods-filtered-power.csv";
                        try {
                            BufferedWriter out = new BufferedWriter(new FileWriter(fileNameMethodsFiltered, false));
                            out.write(bufMethFiltered.toString());
                            out.close();
                        } catch (Exception ignored) {}

                        // Sleep for 10 milliseconds
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();

        /**
         * Code to execute when exiting the program and the agent
         */
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Kill power monitor on Windows process if ever used
                try {
                    powerMonitorWindowsProcess.destroy();
                } catch (Exception ignoredException) {}

                System.out.println("+---------------------------------+");
                System.out.println("JoularJX finished monitoring application with ID " + appPid);
                System.out.println("Program consumed " + String.format("%.2f", totalProcessEnergy) + " joules");

                // Prepare buffer for methods energy
                StringBuffer buf = new StringBuffer();
                for (Map.Entry<String, Double> entry : methodsEnergy.entrySet()) {
                    String key = entry.getKey();
                    Double value = entry.getValue();
                    buf.append(key + "," + value + "\n");
                }

                // Write to CSV file
                String fileNameMethods = "joularJX-" + appPid + "-methods-energy.csv";
                try {
                    BufferedWriter out = new BufferedWriter(new FileWriter(fileNameMethods, true));
                    out.write(buf.toString());
                    out.close();
                } catch (Exception ignored) {}

                // Prepare buffer for filtered methods energy
                StringBuffer bufFil = new StringBuffer();
                for (Map.Entry<String, Double> entry : methodsEnergyFiltered.entrySet()) {
                    String key = entry.getKey();
                    Double value = entry.getValue();
                    bufFil.append(key + "," + value + "\n");
                }

                // Write to CSV file for filtered methods
                String fileNameMethodsFiltered = "joularJX-" + appPid + "-methods-energy-filtered.csv";
                try {
                    BufferedWriter out = new BufferedWriter(new FileWriter(fileNameMethodsFiltered, true));
                    out.write(bufFil.toString());
                    out.close();
                } catch (Exception ignored) {}

                System.out.println("Energy consumption of methods and filtered methods written to " + fileNameMethods + " and " + fileNameMethodsFiltered + " files");
                System.out.println("+---------------------------------+");
            }
        });
    }

    /**
     * Read power data from PowerJoular CSV file
     * @param fileName Path and name of PowerJoular power CSV file
     * @return Power consumption as reported by PowerJoular for the CPU
     */
    public double getPowerFromCSVFile(String fileName) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(fileName));
            // Only read first line
            String line = br.readLine();
            if ((line != null) && (line.length() > 0)) {
                String[] values = line.split(",");
                br.close();
                // Line should have 3 values: date, CPU utilization and power
                // Example: 2021-04-28 15:40:45;0.08023;17.38672
                return Double.parseDouble(values[2]);
            }
            br.close();
            return 0;
        } catch (Exception e) {
            // First few times, CSV file isn't created yet
            // Also first time PowerJoular runs will generate a file with text Date, CPU Utilization, CPU Power
            // So, accurate values will be available after around 2-3 seconds
            // We return 0 in this case and in case any error reading the file or PowerJoular not installed
            return 0;
        }
    }

    /**
     * Get energy readings from RAPL through powercap
     * Calculates the best energy reading as supported by CPU (psys, or pkg+dram, or pkg)
     * @return Energy readings from RAPL
     */
    private double getRAPLEnergy() {
        try {
            if (Files.exists(PSYS)) {
                return readJoulesFromFile(PSYS);
            } else {
                double energyData = readJoulesFromFile(PKG);
                if (Files.exists(DRAM)) {
                    energyData += readJoulesFromFile(DRAM);
                }
                return energyData;
            }
        } catch (IOException e) {
            Util.sneakyThrows(e);
        } catch (Exception e) {
            System.out.println("Failed to get RAPL energy readings. Did you run JoularJX with elevated privileges (sudo)?");
            System.exit(1);
        }
        return 0;
    }

    private double readJoulesFromFile(Path path) throws IOException {
        return Double.parseDouble(Files.readString(path)) / MICROJOULES_IN_JOULES;
    }
}
