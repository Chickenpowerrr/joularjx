package org.noureddine.joularjx.result;

import java.io.IOException;

public interface ResultWriter {

  void write(double joules) throws IOException;
}
