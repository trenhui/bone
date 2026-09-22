package com.bone.engine.extension.studio.infrastructure.marketplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.engine.extension.studio.domain.model.marketplace.MarketplaceItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class JsonResourceMarketplaceCatalogTest {

  private static final String JSON =
      """
            [
              {
                "id":"sample.foo",
                "name":"Foo",
                "description":"foo desc",
                "version":"1.0.0",
                "vendor":"Bone",
                "category":"discount",
                "tags":["a","b"],
                "extPointInterface":"com.bone.example.PromotionExt",
                "className":"com.example.FooImpl",
                "installed":false
              },
              {
                "id":"sample.bar",
                "name":"Bar",
                "description":"bar desc",
                "version":"2.0.0",
                "vendor":"Bone",
                "category":"observability",
                "tags":[],
                "extPointInterface":"com.bone.example.AuditExt",
                "className":"com.example.BarImpl",
                "installed":false
              }
            ]
            """;

  private JsonResourceMarketplaceCatalog newCatalog() {
    return new JsonResourceMarketplaceCatalog(
        new ObjectMapper(), new ByteArrayResource(JSON.getBytes()));
  }

  @Test
  @DisplayName("list 不带过滤返回全部条目")
  void listAll() {
    List<MarketplaceItem> items = newCatalog().list(null, null);
    assertEquals(2, items.size());
  }

  @Test
  @DisplayName("按 keyword/category 过滤")
  void listWithFilter() {
    JsonResourceMarketplaceCatalog catalog = newCatalog();
    assertEquals(1, catalog.list("foo", null).size());
    assertEquals(1, catalog.list(null, "discount").size());
    assertEquals(0, catalog.list("foo", "observability").size());
  }

  @Test
  @DisplayName("findById 命中与未命中")
  void findById() {
    JsonResourceMarketplaceCatalog catalog = newCatalog();
    assertTrue(catalog.findById("sample.foo").isPresent());
    assertFalse(catalog.findById("missing").isPresent());
  }
}
