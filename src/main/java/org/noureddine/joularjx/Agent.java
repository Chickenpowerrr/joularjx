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
import java.io.FileReader;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class Agent {

    private static final Path RAPL_PATH = Path.of("/sys/class/powercap/intel-rapl/intel-rapl:0");
    private static final Path CONFIG_PATH = Path.of("./config.properties");

    /**
     * Map to store total energy for each method
     */
    private final Map<String, Double> methodsEnergy;

    /**
     * Map to store total energy for filtered methods
     */
    private final Map<String, Double> methodsEnergyFiltered;

    /**
     * Sensor to use for monitor CPU energy/power consumption
     */
    private final String energySensor;

    /**
     * List of methods to filter for energy
     */
    private final List<String> filterMethodNames;

    /**
     * Path for our power monitor program on Windows
     */
    private final String powerMonitorPathWindows;

    /**
     * Variables to collect the program energy consumption
     */
    private final AtomicDouble totalProcessEnergy;

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
        Thread.currentThread().setName("JoularJX Agent Thread");
        System.out.println("+---------------------------------+");
        System.out.println("| JoularJX Agent Version 1.0      |");
        System.out.println("+---------------------------------+");

        new Agent().run();
    }

    public Agent() {
        this.methodsEnergy = new ConcurrentHashMap<>();
        this.methodsEnergyFiltered = new ConcurrentHashMap<>();
        this.totalProcessEnergy = new AtomicDouble();

        Properties properties = getProperties();

        this.energySensor = validateEnergySensor();
        this.filterMethodNames = Arrays.asList(properties.getProperty("filter-method-names").split(","));
        this.powerMonitorPathWindows = properties.getProperty("powermonitor-path");
    }

    private Properties getProperties() {
        // Read properties file
        Properties properties = new Properties();
        Util.doSneaky(() -> properties.load(Files.newBufferedReader(CONFIG_PATH)));
        return properties;
    }

    public void run() {
        System.out.println("Please wait while initializing JoularJX...");

        ThreadMXBean mxbean = enableCpuTime();
        long appPid = ProcessHandle.current().pid();
        OperatingSystemMXBean osMxBean = getOSMXBean();

        System.out.println("Initialization finished");

        new Thread(
            new PowerConsumptionHandler(appPid, energySensor, totalProcessEnergy, filterMethodNames,
                methodsEnergy, methodsEnergyFiltered, mxbean, osMxBean,
                powerMonitorWindowsProcess)).start();
        Runtime.getRuntime().addShutdownHook(new Thread(
            new ShutdownHandler(appPid, totalProcessEnergy, powerMonitorWindowsProcess,
                methodsEnergy, methodsEnergyFiltered)));
    }

    private String validateEnergySensor() {
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
                if (Files.exists(RAPL_PATH)) {
                    return "rapl";
                } else {
                    System.out.println("Platform not supported. Existing...");
                    System.exit(1);
                }
            }
        }

        if (osName.contains("win")) {
            return "windows";
        }

        System.out.println("Platform not supported. Existing...");
        System.exit(1);
        return null;
    }

    private ThreadMXBean enableCpuTime() {
        ThreadMXBean mxbean = ManagementFactory.getThreadMXBean();
        if (!mxbean.isThreadCpuTimeSupported()) {
            System.out.println("Thread CPU Time is not supported on this Java Virtual Machine. Existing...");
            System.exit(1);
        }

        if (!mxbean.isThreadCpuTimeEnabled()) {
            mxbean.setThreadCpuTimeEnabled(true);
        }

        return mxbean;
    }

    private OperatingSystemMXBean getOSMXBean() {
        // Get OS MxBean to collect CPU and Process loads
        OperatingSystemMXBean osMxBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        // Loop for a couple of seconds to initialize OSMXBean to get accurate details (first call will return -1)
        for (int i = 0; i < 2; i++) {
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
            try {
                Thread.sleep(500);
            } catch (Exception ignoredException) {}
        }
        return osMxBean;
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
}
