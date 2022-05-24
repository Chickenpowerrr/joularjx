package org.noureddine.joularjx.util;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class AtomicDouble extends Number {

  private static final VarHandle VALUE;

  private volatile double value;

  static {
    VALUE = Sneaky.perform(() -> MethodHandles.lookup()
        .findVarHandle(AtomicDouble.class, "value", double.class));
  }

  public AtomicDouble() {

  }

  public final double get() {
    return value;
  }

  public final double add(double add) {
    double startValue = value;
    while (!VALUE.compareAndSet(startValue, startValue + add)) {
      startValue = value;
    }

    return startValue + add;
  }

  @Override
  public int intValue() {
    return (int) value;
  }

  @Override
  public long longValue() {
    return (long) value;
  }

  @Override
  public float floatValue() {
    return (float) value;
  }

  @Override
  public double doubleValue() {
    return value;
  }
}
