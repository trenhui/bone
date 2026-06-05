package com.bone.metadata.catalog.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CatalogHttpSupportTest {

  @Test
  void parseIfMatchVersion_acceptsQuotedVPrefix() {
    assertEquals(3, CatalogHttpSupport.parseIfMatchVersion("\"v3\"").orElseThrow());
  }

  @Test
  void parseIfMatchVersion_acceptsWeakEtag() {
    assertEquals(5, CatalogHttpSupport.parseIfMatchVersion("W/\"v5\"").orElseThrow());
  }

  @Test
  void parseIfMatchVersion_emptyWhenMissing() {
    assertTrue(CatalogHttpSupport.parseIfMatchVersion(null).isEmpty());
  }

  @Test
  void formatEtag_matchesParser() {
    assertEquals("\"v2\"", CatalogHttpSupport.formatEtag(2));
  }
}
