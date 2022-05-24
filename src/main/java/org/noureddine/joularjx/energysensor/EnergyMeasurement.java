package org.noureddine.joularjx.energysensor;

public class EnergyMeasurement {

  private final double cpuEnergy;
  private final double cpuLoad;
  private final double processCpuLoad;

  public EnergyMeasurement(double cpuEnergy, double cpuLoad, double processCpuLoad) {
    this.cpuEnergy = cpuEnergy;
    this.cpuLoad = cpuLoad;
    this.processCpuLoad = processCpuLoad;
  }

  public double getProcessCpuEnergy() {
    return (processCpuLoad * cpuEnergy) / cpuLoad;
  }

  public double getCpuEnergy() {
    return cpuEnergy;
  }

  public double getCpuLoad() {
    return cpuLoad;
  }

  public double getProcessCpuLoad() {
    return processCpuLoad;
  }
}
