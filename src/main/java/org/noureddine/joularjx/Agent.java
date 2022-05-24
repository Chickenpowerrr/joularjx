/*
 * Copyright (c) 2021-2022, Adel Noureddine, Université de Pays et des Pays de l'Adour.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the
 * GNU General Public License v3.0 only (GPL-3.0-only)
 * which accompanies this distribution, and is available at
 * https://www.gnu.org/licenses/gpl-3.0.en.html
 *
 * Author : Adel Noureddine
 */

package org.noureddine.joularjx;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.noureddine.joularjx.energysensor.EnergySensor;
import org.noureddine.joularjx.energysensor.EnergySensorFactory;

public class Agent {

    private static final Path CONFIG_PATH = Path.of("./config.properties");

    /**
     * Sensor to use for monitor CPU energy/power consumption
     */
    private final EnergySensor energySensor;

    /**
     * JVM hook to statically load the java agent at startup.
     * After the Java Virtual Machine (JVM) has initialized, the premain method
     * will be called. Then the real application main method will be called.
     */
    public static void premain(String args, Instrumentation inst) {
        Thread.currentThread().setName("JoularJX Agent Thread");
        System.out.println("+---------------------------------+");
        System.out.println("| JoularJX Agent Version 1.0      |");
        System.out.println("+---------------------------------+");

        new Agent().run();
    }

    public Agent() {
        this.energySensor = EnergySensorFactory.getInstance().getEnergySensor(
            getProperties().getProperty("powermonitor-path"));
    }

    private Properties getProperties() {
        // Read properties file
        Properties properties = new Properties();
        Sneaky.perform(() -> properties.load(Files.newBufferedReader(CONFIG_PATH)));
        return properties;
    }

    public void run() {
        System.out.println("Please wait while initializing JoularJX...");

        AtomicDouble totalProcessEnergy = new AtomicDouble();
        long appPid = ProcessHandle.current().pid();
        enableCpuTime();

        System.out.println("Initialization finished");

        new Thread(new PowerConsumptionHandler(appPid, energySensor, totalProcessEnergy)).start();
        Runtime.getRuntime().addShutdownHook(new Thread(
            new ShutdownHandler(appPid, totalProcessEnergy, energySensor)));
    }

    private void enableCpuTime() {
        ThreadMXBean mxbean = ManagementFactory.getThreadMXBean();
        if (!mxbean.isThreadCpuTimeSupported()) {
            System.out.println("Thread CPU Time is not supported on this Java Virtual Machine. Existing...");
            System.exit(1);
        }

        if (!mxbean.isThreadCpuTimeEnabled()) {
            mxbean.setThreadCpuTimeEnabled(true);
        }
    }

    /**
     * Read power data from PowerJoular CSV file
     * @param fileName Path and name of PowerJoular power CSV file
     * @return Power consumption as reported by PowerJoular for the CPU
     */
    public double getPowerFromCSVFile(String fileName) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(fileName));
            // Only read first line
            String line = br.readLine();
            if ((line != null) && (line.length() > 0)) {
                String[] values = line.split(",");
                br.close();
                // Line should have 3 values: date, CPU utilization and power
                // Example: 2021-04-28 15:40:45;0.08023;17.38672
                return Double.parseDouble(values[2]);
            }
            br.close();
            return 0;
        } catch (Exception e) {
            // First few times, CSV file isn't created yet
            // Also first time PowerJoular runs will generate a file with text Date, CPU Utilization, CPU Power
            // So, accurate values will be available after around 2-3 seconds
            // We return 0 in this case and in case any error reading the file or PowerJoular not installed
            return 0;
        }
    }
}
