package org.noureddine.joularjx;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.Thread.State;
import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PowerConsumptionHandler implements Runnable {

  private static final Path PSYS_PATH = Path.of("/sys/class/powercap/intel-rapl/intel-rapl:1/energy_uj");
  private static final Path PKG_PATH = Path.of("/sys/class/powercap/intel-rapl/intel-rapl:0/energy_uj");
  private static final Path DRAM_PATH = Path.of("/sys/class/powercap/intel-rapl/intel-rapl:0/intel-rapl:0:2/energy_uj");
  private static final double MICROJOULES_IN_JOULE = 1e6;
  /**
   * Global monitor used to implement mutual-exclusion. In the future this single
   * monitor may be broken up into many different monitors to reduce contention.
   */
  private static final Object LOCK = new Object();

  private final long appPid;
  private final String energySensor;
  private final AtomicDouble totalProcessEnergy;
  private final List<String> filterMethodNames;
  private final Map<String, Double> methodsEnergy;
  private final Map<String, Double> methodsEnergyFiltered;
  private final ThreadMXBean mxBean;
  private final OperatingSystemMXBean osMxBean;
  private final Process powerMonitorWindowsProcess;

  public PowerConsumptionHandler(long appPid, String energySensor, AtomicDouble totalProcessEnergy,
      List<String> filterMethodNames,
      Map<String, Double> methodsEnergy, Map<String, Double> methodsEnergyFiltered,
      ThreadMXBean mxBean, OperatingSystemMXBean osMxBean, Process powerMonitorWindowsProcess) {
    this.appPid = appPid;
    this.energySensor = energySensor;
    this.totalProcessEnergy = totalProcessEnergy;
    this.filterMethodNames = filterMethodNames;
    this.methodsEnergy = methodsEnergy;
    this.methodsEnergyFiltered = methodsEnergyFiltered;
    this.mxBean = mxBean;
    this.osMxBean = osMxBean;
    this.powerMonitorWindowsProcess = powerMonitorWindowsProcess;
  }

  @Override
  public void run() {
    Thread.currentThread().setName("JoularJX Agent Computation");
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
                if (isFilteredMethod(methName)) {
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
        double cpuEnergy = 0.0;
        double cpuLoad = osMxBean.getCpuLoad();
        double processCpuLoad = osMxBean.getProcessCpuLoad();

        switch (energySensor) {
          case "rapl":
            // At the end of the monitoring loop
            energyAfter = getRAPLEnergy();
            // Calculate total energy consumed in the monitoring loop
            cpuEnergy = energyAfter - energyBefore;
            break;
          case "windows":
            // Get CPU energy consumption on Windows using program monitor
            try {
              BufferedReader input = new BufferedReader(new InputStreamReader(powerMonitorWindowsProcess.getInputStream()));
              String line = input.readLine();
              cpuEnergy = Double.parseDouble(line);
            } catch (Exception ignoredException) {
              ignoredException.printStackTrace();
            }
            break;
          default:
            break;
        }

        // Calculate CPU energy consumption of the process of the JVM all its apps
        double processEnergy = calculateProcessCPUEnergy(cpuLoad, processCpuLoad, cpuEnergy);

        // Adds current power to total energy
        totalProcessEnergy.add(processEnergy);

        // Now we have:
        // CPU energy for JVM process
        // CPU energy for all processes
        // We need to calculate energy for each thread
        long totalThreadsCPUTime = 0;
        for (Thread t : threads) {
          long threadCPUTime = mxBean.getThreadCpuTime(t.getId());

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
          double threadPower = processEnergy * (percentageCPUTime / 100.0);
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

  /**
   * Get energy readings from RAPL through powercap
   * Calculates the best energy reading as supported by CPU (psys, or pkg+dram, or pkg)
   * @return Energy readings from RAPL
   */
  private double getRAPLEnergy() {
    try {
      if (Files.exists(PSYS_PATH)) {
        return readJoulesFromFile(PSYS_PATH);
      } else {
        double energyData = readJoulesFromFile(PKG_PATH);
        if (Files.exists(DRAM_PATH)) {
          energyData += readJoulesFromFile(DRAM_PATH);
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
    return Double.parseDouble(Files.readString(path)) / MICROJOULES_IN_JOULE;
  }

  /**
   * Calculate process energy consumption
   * @param totalCPUUsage Total CPU usage
   * @param processCPUUSage Process CPU usage
   * @param CPUEnergy CPU energy
   * @return Process energy consumption
   */
  private double calculateProcessCPUEnergy(double totalCPUUsage, double processCPUUSage, double CPUEnergy) {
    return (processCPUUSage * CPUEnergy) / totalCPUUsage;
  }

  /**
   * Check if methodName starts with one of the filtered method names
   * @param methodName Name of method
   * @return True if methodName starts with one of the filtered method names, false if not
   */
  private boolean isFilteredMethod(String methodName) {
    for (String filterMethod : filterMethodNames) {
      if (methodName.startsWith(filterMethod)) {
        return true;
      }
    }
    return false;
  }
}
