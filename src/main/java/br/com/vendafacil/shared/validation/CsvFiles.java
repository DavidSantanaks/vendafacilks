package br.com.vendafacil.shared.validation;

import java.util.List;

public final class CsvFiles {
  private CsvFiles() {}

  public static String cell(String value) {
    var safe = value == null ? "" : value;
    if (safe.stripLeading().matches("(?s)^[=+@\\-].*")
        || safe.startsWith("\t")
        || safe.startsWith("\r")
        || safe.startsWith("\n")) {
      safe = "'" + safe;
    }
    return "\"" + safe.replace("\"", "\"\"") + "\"";
  }

  public static String line(List<String> values) {
    return String.join(";", values.stream().map(CsvFiles::cell).toList()) + "\r\n";
  }
}
