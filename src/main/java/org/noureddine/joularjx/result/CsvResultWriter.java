package org.noureddine.joularjx.result;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class CsvResultWriter implements ResultWriter {

  private final Path path;

  public CsvResultWriter(long appPid) throws IOException {
    this.path = Path.of("joularJX-" + appPid + "-power.csv");

    try (BufferedWriter writer = Files.newBufferedWriter(path)) {
      writer.write("joules\n");
    }
  }

  @Override
  public void write(double joules) throws IOException {
    try (BufferedWriter writer = Files.newBufferedWriter(path, StandardOpenOption.APPEND)) {
      writer.write(String.format("%.4f\n", joules));
    }
  }
}
