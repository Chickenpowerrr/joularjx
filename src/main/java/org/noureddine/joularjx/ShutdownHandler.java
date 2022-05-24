package org.noureddine.joularjx;

import java.io.IOException;
import org.noureddine.joularjx.energysensor.EnergySensor;

public class ShutdownHandler implements Runnable {

  private final long appPid;
  private final EnergySensor energySensor;
  private final AtomicDouble totalProcessEnergy;

  public ShutdownHandler(long appPid, AtomicDouble totalProcessEnergy, EnergySensor energySensor) {
    this.appPid = appPid;
    this.totalProcessEnergy = totalProcessEnergy;
    this.energySensor = energySensor;
  }

  @Override
  public void run() {
    try {
      energySensor.close();
    } catch (IOException ignoredException) {}

    System.out.println("+---------------------------------+");
    System.out.println("JoularJX finished monitoring application with ID " + appPid);
    System.out.println("Program consumed " + String.format("%.2f", totalProcessEnergy.doubleValue()) + " joules");
  }
}
