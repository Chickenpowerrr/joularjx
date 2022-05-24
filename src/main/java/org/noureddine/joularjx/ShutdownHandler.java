package org.noureddine.joularjx;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Map;

public class ShutdownHandler implements Runnable {

  private final long appPid;
  private final AtomicDouble totalProcessEnergy;
  private final Process powerMonitorWindowsProcess;
  private final Map<String, Double> methodsEnergy;
  private final Map<String, Double> methodsEnergyFiltered;

  public ShutdownHandler(long appPid, AtomicDouble totalProcessEnergy,
      Process powerMonitorWindowsProcess, Map<String, Double> methodsEnergy,
      Map<String, Double> methodsEnergyFiltered) {
    this.appPid = appPid;
    this.totalProcessEnergy = totalProcessEnergy;
    this.powerMonitorWindowsProcess = powerMonitorWindowsProcess;
    this.methodsEnergy = methodsEnergy;
    this.methodsEnergyFiltered = methodsEnergyFiltered;
  }

  @Override
  public void run() {
    if (powerMonitorWindowsProcess != null) {
      powerMonitorWindowsProcess.destroy();
    }

    System.out.println("+---------------------------------+");
    System.out.println("JoularJX finished monitoring application with ID " + appPid);
    System.out.println("Program consumed " + String.format("%.2f", totalProcessEnergy.doubleValue()) + " joules");

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
}
