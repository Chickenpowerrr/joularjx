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

import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.noureddine.joularjx.energysensor.EnergySensor;
import org.noureddine.joularjx.energysensor.EnergySensorFactory;
import org.noureddine.joularjx.result.CsvResultWriter;
import org.noureddine.joularjx.result.ResultWriter;

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
        ResultWriter resultWriter = Sneaky.perform(() -> new CsvResultWriter(appPid));
        PowerConsumptionHandler powerConsumptionHandler = new PowerConsumptionHandler(
            appPid, energySensor, totalProcessEnergy);
        ShutdownHandler shutdownHandler = new ShutdownHandler(
            appPid, totalProcessEnergy, energySensor, resultWriter);

        System.out.println("Initialization finished");

        new Thread(powerConsumptionHandler).start();
        Runtime.getRuntime().addShutdownHook(new Thread(shutdownHandler));
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
}
