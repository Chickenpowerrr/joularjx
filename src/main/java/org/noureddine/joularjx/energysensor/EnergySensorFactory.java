package org.noureddine.joularjx.energysensor;

import java.nio.file.Files;

public final class EnergySensorFactory {

  private static final EnergySensorFactory instance = new EnergySensorFactory();

  private EnergySensor energySensor;

  public static EnergySensorFactory getInstance() {
    return instance;
  }

  private EnergySensorFactory() {
    this.energySensor = null;
  }

  public EnergySensor getEnergySensor(String powerMonitorPathWindows) {
    if (energySensor != null) {
      return energySensor;
    }

    String osName = System.getProperty("os.name").toLowerCase();
    String osArch = System.getProperty("os.arch").toLowerCase();

    if (osName.contains("linux")) {
      // GNU/Linux
      if (osArch.contains("aarch64") || osArch.contains("arm")) {
        // Platform not supported
        System.out.println("Platform not supported. Existing...");
        System.exit(1);
      } else {
        // Suppose it's x86/64, check for powercap RAPL
        if (Files.exists(RaplEnergySensor.RAPL_PATH)) {
          this.energySensor = new RaplEnergySensor();
          return energySensor;
        } else {
          System.out.println("Platform not supported. Existing...");
          System.exit(1);
        }
      }
    }

    if (osName.contains("win")) {
      this.energySensor = new WindowsEnergySensor(powerMonitorPathWindows);
      return energySensor;
    }

    System.out.println("Platform not supported. Existing...");
    System.exit(1);
    return null;
  }
}
