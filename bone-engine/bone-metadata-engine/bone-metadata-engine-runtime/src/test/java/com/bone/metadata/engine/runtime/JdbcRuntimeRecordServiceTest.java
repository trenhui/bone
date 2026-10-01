package com.bone.metadata.engine.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.h2.Driver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

class JdbcRuntimeRecordServiceTest {

  private JdbcRuntimeRecordService service;

  @BeforeEach
  void setUp() {
    // 用 MySQL 兼容模式 + DATABASE_TO_LOWER，使 H2 接受生产代码的反引号语法并返回小写列标签，
    // 对齐生产 MySQL 语义（H2 默认模式既不支持反引号、又会将未加引标识符转大写，导致 queryForList
    // 返回的 Map key 为大写、与逻辑 code 不匹配）。
    var url = "jdbc:h2:mem:demo_order;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    var ds = new SimpleDriverDataSource(new Driver(), url);
    var jdbc = new NamedParameterJdbcTemplate(ds);
    jdbc.getJdbcTemplate().execute("DROP TABLE IF EXISTS demo_order");
    jdbc.getJdbcTemplate()
        .execute(
            "CREATE TABLE demo_order ("
                + "`id` BIGINT PRIMARY KEY, `tenant_id` BIGINT NOT NULL, "
                + "`order_no` VARCHAR(64) NOT NULL, `amount` DOUBLE, `version` INT NOT NULL DEFAULT 0, "
                + "`deleted` SMALLINT NOT NULL DEFAULT 0)");
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
                      new RuntimeFieldColumn("id", "LONG", true, false, true),
                      new RuntimeFieldColumn("order_no", "STRING", true, true, false),
                      new RuntimeFieldColumn("amount", "DOUBLE", false, false, false))));
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
  void shouldRejectMissingRequiredField() {
    assertThrows(
        RuntimeRecordException.class,
        () -> service.create("demo_order", 1L, Map.of("amount", 5), 2001L));
  }

  @Test
  void shouldRejectDuplicateUniqueField() {
    service.create("demo_order", 1L, Map.of("order_no", "DUP-1", "amount", 1), 2002L);
    assertThrows(
        RuntimeRecordException.class,
        () -> service.create("demo_order", 1L, Map.of("order_no", "DUP-1", "amount", 2), 2003L));
  }

  @Test
  void shouldRejectUnknownEntity() {
    assertThrows(RuntimeRecordException.class, () -> service.page("unknown", 1L, 1, 10));
  }

  @Test
  void shouldFilterAndSortWithQueryParams() {
    service.create("demo_order", 1L, Map.of("order_no", "O-A", "amount", 10), 1L);
    service.create("demo_order", 1L, Map.of("order_no", "O-B", "amount", 20), 2L);

    var query = RuntimePageQuery.parse("order_no,amount", "amount", "order_no:O-B");
    var page = service.page("demo_order", 1L, 1, 10, query);

    assertEquals(1, page.getRecords().size());
    assertEquals("O-B", page.getRecords().get(0).get("order_no"));
  }

  @Test
  void shouldRejectVersionMismatchOnUpdate() {
    service.create("demo_order", 1L, Map.of("order_no", "O-1", "amount", 1), 100L);
    assertThrows(
        RuntimeRecordException.class,
        () -> service.update("demo_order", 1L, "100", Map.of("amount", 2), 999));
  }
}
