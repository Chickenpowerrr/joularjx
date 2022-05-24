package org.noureddine.joularjx.energysensor;

import com.sun.management.OperatingSystemMXBean;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import util.Sneaky;

public class WindowsEnergySensor implements EnergySensor {

  private final OperatingSystemMXBean osMxBean;
  private final Process powerMonitorProcess;
  private final ThreadLocal<Long> startTimeNanos;

  public WindowsEnergySensor(String powerMonitorPath) {
    this.osMxBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    this.powerMonitorProcess = loadPowerMonitorProcess(powerMonitorPath);
    this.startTimeNanos = ThreadLocal.withInitial(() -> 0L);
  }

  @Override
  public void startMeasurement() {
    startTimeNanos.set(System.nanoTime());
  }

  @Override
  public EnergyMeasurement endMeasurement() {
    double energy = getEnergy();
    long durationNanos = System.nanoTime() - startTimeNanos.get();
    double cpuLoad = osMxBean.getCpuLoad();
    double processCpuLoad = osMxBean.getProcessCpuLoad();

    return new EnergyMeasurement(durationNanos, energy, cpuLoad, processCpuLoad);
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

  private double getEnergy() {
    try {
      BufferedReader input = new BufferedReader(
          new InputStreamReader(powerMonitorProcess.getInputStream()));
      String line = input.readLine();
      return Double.parseDouble(line);
    } catch (Exception e) {
      return Sneaky.throwing(e);
    }
  }
}
