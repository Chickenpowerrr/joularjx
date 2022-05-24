package org.noureddine.joularjx.energysensor;

import java.io.Closeable;
import java.io.IOException;

public interface EnergySensor extends Closeable {

  void startMeasurement();

  EnergyMeasurement endMeasurement();

  @Override
  default void close() throws IOException {

  }
}
