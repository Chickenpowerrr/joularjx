package org.noureddine.joularjx.energysensor;

public class EnergyMeasurement {

  private static final double NANOS_IN_SECOND = 1e9;

  private final long durationNanos;
  private final double cpuEnergy;
  private final double cpuLoad;
  private final double processCpuLoad;

  public EnergyMeasurement(long durationNanos, double cpuEnergy,
      double cpuLoad, double processCpuLoad) {
    this.durationNanos = durationNanos;
    this.cpuEnergy = cpuEnergy;
    this.cpuLoad = cpuLoad;
    this.processCpuLoad = processCpuLoad;
  }

  public double getProcessCpuEnergy() {
    return (durationNanos / NANOS_IN_SECOND) * (processCpuLoad * cpuEnergy) / cpuLoad;
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

  public long getDurationNanos() {
    return durationNanos;
  }
}
