package org.noureddine.joularjx.energysensor;

import com.sun.management.OperatingSystemMXBean;
import java.io.Closeable;
import java.io.IOException;

public interface EnergySensor extends Closeable {

  OperatingSystemMXBean getOsMxBean();

  void startMeasurement();

  EnergyMeasurement endMeasurement();

  @Override
  default void close() throws IOException {

  }
}
