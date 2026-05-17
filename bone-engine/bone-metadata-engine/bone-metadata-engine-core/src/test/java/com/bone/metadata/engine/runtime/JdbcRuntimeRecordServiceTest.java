package com.bone.metadata.engine.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class JdbcRuntimeRecordServiceTest {

  private JdbcRuntimeRecordService service;

  @BeforeEach
  void setUp() {
    var db =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .build();
    var jdbc = new NamedParameterJdbcTemplate(db);
    jdbc.getJdbcTemplate().execute("DROP TABLE IF EXISTS demo_order");
    jdbc.getJdbcTemplate()
        .execute(
            "CREATE TABLE demo_order ("
                + "id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, "
                + "order_no VARCHAR(64) NOT NULL, amount DOUBLE, deleted SMALLINT NOT NULL DEFAULT 0)");
    RuntimeEntityCatalog catalog =
        (code, tenantId) -> {
          if (!"demo_order".equals(code)) {
            return Optional.empty();
          }
          return Optional.of(
              new PublishedRuntimeEntity(
                  "demo_order",
                  "demo_order",
                  "id",
                  tenantId,
                  List.of(
                      new RuntimeFieldColumn("id", true, true),
                      new RuntimeFieldColumn("order_no", true, false),
                      new RuntimeFieldColumn("amount", false, false))));
        };
    service = new JdbcRuntimeRecordService(jdbc, catalog);
  }

  @Test
  void shouldCreateAndGetRecord() {
    Map<String, Object> created =
        service.create("demo_order", 1L, Map.of("order_no", "O-1", "amount", 99), 1001L);
    assertEquals(1001L, ((Number) created.get("id")).longValue());

    Map<String, Object> loaded = service.getById("demo_order", 1L, "1001");
    assertEquals("O-1", loaded.get("order_no"));
  }

  @Test
  void shouldRejectUnknownEntity() {
    assertThrows(
        RuntimeRecordException.class, () -> service.page("unknown", 1L, 1, 10));
  }
}
