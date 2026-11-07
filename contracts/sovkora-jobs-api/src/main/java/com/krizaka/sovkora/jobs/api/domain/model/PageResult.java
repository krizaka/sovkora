package com.krizaka.sovkora.jobs.api.api.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * One page of a listing, in plain Java (sovkora-engine#121: no framework type crosses a contract).
 * The field names are the ones the web client already reads from a serialised Spring {@code Page}:
 * {@code content}, {@code number}, {@code size}, {@code totalElements}, {@code totalPages}.
 *
 * @param content the items of this page, never null
 * @param number zero-based index of this page
 * @param size the requested page size, at least 1
 * @param totalElements how many items match across all pages
 */
public record PageResult<T>(List<T> content, int number, int size, long totalElements) {

  public PageResult {
    Objects.requireNonNull(content, "content");
    if (number < 0) {
      throw new IllegalArgumentException("number must not be negative: " + number);
    }
    if (size < 1) {
      throw new IllegalArgumentException("size must be at least 1: " + size);
    }
    if (totalElements < 0) {
      throw new IllegalArgumentException("totalElements must not be negative: " + totalElements);
    }
    content = List.copyOf(content);
  }

  /** How many pages the listing has. */
  public int totalPages() {
    return (int) Math.ceil((double) totalElements / size);
  }

  /** The same page with every item converted. */
  public <R> PageResult<R> map(Function<? super T, ? extends R> converter) {
    return new PageResult<>(
        content.stream().<R>map(converter).toList(), number, size, totalElements);
  }
}
