package org.noureddine.joularjx;

import java.util.concurrent.Callable;

public final class Util {

  @SuppressWarnings("unchecked")
  public static <T extends Throwable> void sneakyThrows(Throwable t) throws T {
    throw (T) t;
  }

  @SuppressWarnings("unchecked")
  public static <T extends Throwable, R> R doSneaky(Callable<R> callable) throws T {
    try {
      return callable.call();
    } catch (Exception e) {
      throw (T) e;
    }
  }

  @SuppressWarnings("unchecked")
  public static <T extends Throwable> void doSneaky(ThrowingCallable callable) throws T {
    try {
      callable.call();
    } catch (Exception e) {
      throw (T) e;
    }
  }

  @FunctionalInterface
  public interface ThrowingCallable {
    void call() throws Exception;
  }

  private Util() {

  }
}
