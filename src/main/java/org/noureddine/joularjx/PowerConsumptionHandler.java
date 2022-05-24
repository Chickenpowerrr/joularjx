package org.noureddine.joularjx;

import java.lang.Thread.State;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.noureddine.joularjx.energysensor.EnergyMeasurement;
import org.noureddine.joularjx.energysensor.EnergySensor;

public class PowerConsumptionHandler implements Runnable {

  private final long appPid;
  private final EnergySensor energySensor;
  private final AtomicDouble totalProcessEnergy;
  private final List<String> filterMethodNames;
  private final Map<String, Double> methodsEnergy;
  private final Map<String, Double> methodsEnergyFiltered;
  private final ThreadMXBean mxBean;

  public PowerConsumptionHandler(long appPid, EnergySensor energySensor,
      ThreadMXBean mxBean, AtomicDouble totalProcessEnergy, List<String> filterMethodNames,
      Map<String, Double> methodsEnergy, Map<String, Double> methodsEnergyFiltered) {
    this.appPid = appPid;
    this.energySensor = energySensor;
    this.totalProcessEnergy = totalProcessEnergy;
    this.filterMethodNames = filterMethodNames;
    this.methodsEnergy = methodsEnergy;
    this.methodsEnergyFiltered = methodsEnergyFiltered;
    this.mxBean = mxBean;
  }

  private void runMethod() {

  }

  @Override
  public void run() {
    Thread.currentThread().setName("JoularJX Agent Computation");
    System.out.println("Started monitoring application with ID " + appPid);

    while (true) {
      try {
        Map<Long, Map<String, Integer>> methodsStats = new HashMap<>();
        Map<Long, Map<String, Integer>> methodsStatsFiltered = new HashMap<>();
        Set<Thread> threads = Thread.getAllStackTraces().keySet();

        energySensor.startMeasurement();

        for (int duration = 0; duration < 1000; duration += 10) {
          for (Thread thread : threads) {
            long threadId = thread.getId();
            methodsStats.computeIfAbsent(threadId, id -> new HashMap<>());
            methodsStatsFiltered.computeIfAbsent(threadId, id -> new HashMap<>());

            // Only check runnable threads (not waiting or blocked)
            if (thread.getState() != State.RUNNABLE) {
              continue;
            }

            StackTraceElement[] stackTraceElements = thread.getStackTrace();

            if (stackTraceElements.length > 0) {
              computeStackTrace(stackTraceElements[0], threadId, methodsStats);
            }

            for (StackTraceElement stackTraceElement : stackTraceElements) {
              if (!isFilteredMethod(getMethodName(stackTraceElement))) {
                continue;
              }

              computeStackTrace(stackTraceElement, threadId, methodsStatsFiltered);
              break;
            }
          }

          // Sleep for 10 ms
          Thread.sleep(10);
        }

        EnergyMeasurement measurement = energySensor.endMeasurement();

        // Adds current power to total energy
        totalProcessEnergy.add(measurement.getProcessCpuEnergy());

        Thread.sleep(10);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * Check if methodName starts with one of the filtered method names
   * @param methodName Name of method
   * @return True if methodName starts with one of the filtered method names, false if not
   */
  private boolean isFilteredMethod(String methodName) {
    for (String filterMethod : filterMethodNames) {
      if (methodName.startsWith(filterMethod)) {
        return true;
      }
    }
    return false;
  }

  private void computeStackTrace(StackTraceElement stackTraceElement, long threadId,
      Map<Long, Map<String, Integer>> methods) {
    String methodName = getMethodName(stackTraceElement);
    Map<String, Integer> methodData = methods.get(threadId);

    methodData.putIfAbsent(methodName, 0);
    methodData.computeIfPresent(methodName, (name, count) -> count + 1);
  }

  private String getMethodName(StackTraceElement stackTraceElement) {
    return stackTraceElement.getClassName() + "." + stackTraceElement.getMethodName();
  }
}
