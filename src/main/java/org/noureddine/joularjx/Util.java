package org.noureddine.joularjx;

public final class Util {

  @SuppressWarnings("unchecked")
  public static <T extends Throwable> void sneakyThrows(Throwable t) throws T {
    throw (T) t;
  }

  private Util() {

  }
}
