package com.bone.metadata.catalog.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.metadata.catalog.common.exception.CatalogOptimisticLockException;
import org.junit.jupiter.api.Test;

class CatalogVersionSupportTest {

  @Test
  void assertExpected_skipsWhenHeaderAbsent() {
    CatalogVersionSupport.assertExpected(null, 2);
  }

  @Test
  void assertExpected_throws412OnMismatch() {
    assertThrows(
        CatalogOptimisticLockException.class,
        () -> CatalogVersionSupport.assertExpected(1, 2));
  }

  @Test
  void nextVersion_increments() {
    assertEquals(4, CatalogVersionSupport.nextVersion(3));
  }
}
