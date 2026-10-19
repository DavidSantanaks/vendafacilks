package br.com.vendafacil.shared.validation;

import java.util.List;

public record DataPage<T>(List<T> items, int number, long total, int size) {
  public boolean hasPrevious() {
    return number > 0;
  }

  public boolean hasNext() {
    return (long) (number + 1) * size < total;
  }

  public long pages() {
    return Math.max(1, (total + size - 1) / size);
  }
}
