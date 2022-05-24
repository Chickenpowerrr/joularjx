package org.noureddine.joularjx.result;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Map;

public class CsvResultWriter implements ResultWriter {

  public void write() {
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
  }
}
