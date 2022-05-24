package org.noureddine.joularjx;

import java.io.IOException;
import org.noureddine.joularjx.energysensor.EnergySensor;
import org.noureddine.joularjx.result.ResultWriter;
import org.noureddine.joularjx.util.AtomicDouble;

public class ShutdownHandler implements Runnable {

  private final long appPid;
  private final EnergySensor energySensor;
  private final AtomicDouble totalProcessEnergy;
  private final ResultWriter resultWriter;

  public ShutdownHandler(long appPid, AtomicDouble totalProcessEnergy, EnergySensor energySensor,
      ResultWriter resultWriter) {
    this.appPid = appPid;
    this.totalProcessEnergy = totalProcessEnergy;
    this.energySensor = energySensor;
    this.resultWriter = resultWriter;
  }

  @Override
  public void run() {
    try {
      energySensor.close();
      resultWriter.write(totalProcessEnergy.doubleValue());
    } catch (IOException ignoredException) {}

    System.out.println("+---------------------------------+");
    System.out.println("JoularJX finished monitoring application with ID " + appPid);
    System.out.println("Program consumed " + String.format("%.2f", totalProcessEnergy.doubleValue()) + " joules");
  }
}
