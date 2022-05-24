package org.noureddine.joularjx;

import java.util.Set;
import org.noureddine.joularjx.energysensor.EnergyMeasurement;
import org.noureddine.joularjx.energysensor.EnergySensor;
import org.noureddine.joularjx.util.AtomicDouble;

public class PowerConsumptionHandler implements Runnable {

  private static final String THREAD_NAME = "JoularJX Agent Computation";
  private static final String DESTROY_THREAD_NAME = "DestroyJavaVM";

  private final long appPid;
  private final EnergySensor energySensor;
  private final AtomicDouble totalProcessEnergy;

  public PowerConsumptionHandler(long appPid, EnergySensor energySensor,
      AtomicDouble totalProcessEnergy) {
    this.appPid = appPid;
    this.energySensor = energySensor;
    this.totalProcessEnergy = totalProcessEnergy;
  }

  @Override
  public void run() {
    Thread.currentThread().setName(THREAD_NAME);
    System.out.println("Started monitoring application with ID " + appPid);

    Set<Thread> threads = Thread.getAllStackTraces().keySet();
    while (threads.stream().noneMatch(thread -> thread.getName().equals(DESTROY_THREAD_NAME))) {
      try {
        energySensor.startMeasurement();

        Thread.sleep(10);

        EnergyMeasurement measurement = energySensor.endMeasurement();

        // Adds current power to total energy
        totalProcessEnergy.add(measurement.getProcessCpuEnergy());

        threads = Thread.getAllStackTraces().keySet();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }
}
