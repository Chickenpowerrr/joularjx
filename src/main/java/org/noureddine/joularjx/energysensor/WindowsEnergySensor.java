package org.noureddine.joularjx.energysensor;

import com.sun.management.OperatingSystemMXBean;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;

public class WindowsEnergySensor implements EnergySensor {

  private final OperatingSystemMXBean osMxBean;
  private final Process powerMonitorProcess;

  public WindowsEnergySensor(String powerMonitorPath) {
    this.osMxBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    this.powerMonitorProcess = loadPowerMonitorProcess(powerMonitorPath);
  }

  @Override
  public void startMeasurement() {

  }

  @Override
  public double endMeasurement() {
    try {
      BufferedReader input = new BufferedReader(
          new InputStreamReader(powerMonitorProcess.getInputStream()));
      String line = input.readLine();
      return Double.parseDouble(line);
    } catch (Exception ignoredException) {
      ignoredException.printStackTrace();
      return 0.0;
    }
  }

  @Override
  public OperatingSystemMXBean getOsMxBean() {
    return osMxBean;
  }

  @Override
  public void close() {
    powerMonitorProcess.destroy();
  }

  private Process loadPowerMonitorProcess(String powerMonitorPath) {
    Process powerMonitorProcess = null;

    // Loop for a couple of seconds to initialize OSMXBean to get accurate details (first call will return -1)
    for (int i = 0; i < 2; i++) {
      osMxBean.getCpuLoad();
      osMxBean.getProcessCpuLoad();
      try {
        powerMonitorProcess = Runtime.getRuntime().exec(powerMonitorPath);
      } catch (IOException ex) {
        ex.printStackTrace();
        System.out.println("Can't start power monitor on Windows. Existing...");
        System.exit(1);
      }
      try {
        Thread.sleep(500);
      } catch (Exception ignoredException) {}
    }

    return powerMonitorProcess;
  }
}
