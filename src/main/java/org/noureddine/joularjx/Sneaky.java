package org.noureddine.joularjx;

import java.util.concurrent.Callable;

public final class Sneaky {

  @SuppressWarnings("unchecked")
  public static <T extends Throwable> void throwing(Throwable t) throws T {
    throw (T) t;
  }

  @SuppressWarnings("unchecked")
  public static <T extends Throwable, R> R perform(Callable<R> callable) throws T {
    try {
      return callable.call();
    } catch (Exception e) {
      throw (T) e;
    }
  }

  @SuppressWarnings("unchecked")
  public static <T extends Throwable> void perform(ThrowingRunnable callable) throws T {
    try {
      callable.call();
    } catch (Exception e) {
      throw (T) e;
    }
  }

  @FunctionalInterface
  public interface ThrowingRunnable {
    void call() throws Exception;
  }

  private Sneaky() {

  }
}
