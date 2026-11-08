package com.krizaka.sovkora.jobs.api.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageResultTest {

  @Test
  void countsPagesUpwards() {
    assertEquals(3, new PageResult<>(List.of("a"), 0, 10, 21).totalPages());
    assertEquals(0, new PageResult<String>(List.of(), 0, 10, 0).totalPages());
    assertEquals(2, new PageResult<>(List.of("a"), 1, 10, 20).totalPages());
  }

  @Test
  void mapsTheItemsAndKeepsThePaging() {
    PageResult<Integer> mapped = new PageResult<>(List.of("a", "bb"), 2, 5, 12).map(String::length);
    assertEquals(List.of(1, 2), mapped.content());
    assertEquals(2, mapped.number());
    assertEquals(5, mapped.size());
    assertEquals(12, mapped.totalElements());
  }

  @Test
  void refusesAnImpossiblePage() {
    assertThrows(IllegalArgumentException.class, () -> new PageResult<>(List.of(), -1, 10, 0));
    assertThrows(IllegalArgumentException.class, () -> new PageResult<>(List.of(), 0, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new PageResult<>(List.of(), 0, 10, -1));
  }
}
