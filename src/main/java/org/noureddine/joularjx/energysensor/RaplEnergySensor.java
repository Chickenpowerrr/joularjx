package org.noureddine.joularjx.energysensor;

import com.sun.management.OperatingSystemMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.noureddine.joularjx.Sneaky;

public class RaplEnergySensor implements EnergySensor {

  public static final Path RAPL_PATH = Path.of("/sys/class/powercap/intel-rapl/intel-rapl:0");

  private static final Path PSYS_PATH = Path.of("/sys/class/powercap/intel-rapl/intel-rapl:1/energy_uj");
  private static final Path PKG_PATH = Path.of("/sys/class/powercap/intel-rapl/intel-rapl:0/energy_uj");
  private static final Path DRAM_PATH = Path.of("/sys/class/powercap/intel-rapl/intel-rapl:0/intel-rapl:0:2/energy_uj");
  private static final double MICROJOULES_IN_JOULE = 1e6;

  private final ThreadLocal<Double> start;
  private final OperatingSystemMXBean osMxBean;

  public RaplEnergySensor() {
    this.start = ThreadLocal.withInitial(() -> 0.0);
    this.osMxBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    warmupOsMxBean();
  }

  @Override
  public OperatingSystemMXBean getOsMxBean() {
    return osMxBean;
  }

  @Override
  public void startMeasurement() {
    start.set(getEnergy());
  }

  @Override
  public double endMeasurement() {
    return getEnergy() - start.get();
  }

  private void warmupOsMxBean() {
    // Loop for a couple of seconds to initialize OSMXBean to get accurate details (first call will return -1)
    for (int i = 0; i < 2; i++) {
      osMxBean.getCpuLoad();
      osMxBean.getProcessCpuLoad();
      try {
        Thread.sleep(500);
      } catch (Exception ignoredException) {}
    }
  }

  /**
   * Get energy readings from RAPL through powercap
   * Calculates the best energy reading as supported by CPU (psys, or pkg+dram, or pkg)
   * @return Energy readings from RAPL
   */
  private double getEnergy() {
    try {
      if (Files.exists(PSYS_PATH)) {
        return readJoulesFromFile(PSYS_PATH);
      } else {
        double energyData = readJoulesFromFile(PKG_PATH);
        if (Files.exists(DRAM_PATH)) {
          energyData += readJoulesFromFile(DRAM_PATH);
        }
        return energyData;
      }
    } catch (IOException e) {
      Sneaky.throwing(e);
    } catch (Exception e) {
      System.out.println("Failed to get RAPL energy readings. Did you run JoularJX with elevated privileges (sudo)?");
      System.exit(1);
    }
    return 0;
  }

  private double readJoulesFromFile(Path path) throws IOException {
    return Double.parseDouble(Files.readString(path)) / MICROJOULES_IN_JOULE;
  }
}
