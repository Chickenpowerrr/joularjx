package org.noureddine.joularjx;

import java.util.Set;
import org.noureddine.joularjx.energysensor.EnergyMeasurement;
import org.noureddine.joularjx.energysensor.EnergySensor;

public class PowerConsumptionHandler implements Runnable {

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
    Thread.currentThread().setName("JoularJX Agent Computation");
    System.out.println("Started monitoring application with ID " + appPid);

    Set<Thread> threads = Thread.getAllStackTraces().keySet();
    while (threads.stream().anyMatch(Thread::isAlive)) {
      try {
        energySensor.startMeasurement();

        Thread.sleep(100);

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
