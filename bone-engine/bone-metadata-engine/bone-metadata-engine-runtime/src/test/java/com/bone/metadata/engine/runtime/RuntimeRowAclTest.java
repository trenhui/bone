package com.bone.metadata.engine.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/**
 * 行级 ACL（数据面）· Runtime §4.7.1 —— 本人可见/可写，非本人 404，管理员豁免（operatorId 传 null）。
 *
 * <p>约定：实体表存在 {@code created_by} 列即启用行级 ACL（owner 字段）；匹配用字符串比较， 兼容 BIGINT 与 VARCHAR 两类 created_by
 * 物理形态。
 */
@DisplayName("行级 ACL（数据面）· 本人可见/可写，非本人 404，管理员豁免")
class RuntimeRowAclTest {

  private static final long TENANT_1 = 1L;

  private EmbeddedDatabase db;
  private NamedParameterJdbcTemplate jdbc;
  private RuntimeEntityCatalog catalog;
  private JdbcRuntimeRecordService service;
  private final AtomicLong idGen = new AtomicLong(1000);

  @BeforeEach
  void setUp() {
    db =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName("md_row_acl;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
            .build();
    jdbc = new NamedParameterJdbcTemplate(db);
    jdbc.getJdbcTemplate()
        .execute(
            "CREATE TABLE `bp_order` ("
                + "`id` BIGINT NOT NULL,"
                + "`tenant_id` BIGINT NOT NULL,"
                + "`customer_id` BIGINT,"
                + "`total_amount` DECIMAL(20,6),"
                + "`status` VARCHAR(255),"
                + "`version` INT NOT NULL DEFAULT 0,"
                + "`deleted` SMALLINT NOT NULL DEFAULT 0,"
                + "`created_at` DATETIME,"
                + "`updated_at` DATETIME,"
                + "`created_by` VARCHAR(64),"
                + "`updated_by` VARCHAR(64),"
                + "PRIMARY KEY (`id`))");
    catalog =
        (code, tenantId) ->
            "bp_order".equals(code)
                ? Optional.of(
                    new PublishedRuntimeEntity(
                        "bp_order",
                        "bp_order",
                        "id",
                        TENANT_1,
                        List.of(
                            new RuntimeFieldColumn("id", "LONG", false, false, true),
                            new RuntimeFieldColumn("customer_id", "LONG", true, false, false),
                            new RuntimeFieldColumn("total_amount", "DECIMAL", true, false, false),
                            new RuntimeFieldColumn("status", "STRING", true, false, false))))
                : Optional.empty();
    service = new JdbcRuntimeRecordService(jdbc, catalog);
  }

  @AfterEach
  void tearDown() {
    if (db != null) {
      db.shutdown();
    }
  }

  private String createOrder(String operatorId) {
    Map<String, Object> created =
        service.create(
            "bp_order",
            TENANT_1,
            Map.of("customer_id", 1L, "total_amount", 100.0d, "status", "NEW"),
            idGen.incrementAndGet(),
            operatorId);
    return String.valueOf(created.get("id"));
  }

  @Test
  @DisplayName("本人创建后可见：page 过滤仅返回本人行，getById 可读")
  void owner_seesOwnRows() {
    String id1001 = createOrder("1001");
    createOrder("2002");

    var page = service.page("bp_order", TENANT_1, 1, 20, RuntimePageQuery.EMPTY, 1001L);
    assertEquals(1, page.getTotal());
    assertEquals(id1001, String.valueOf(page.getRecords().get(0).get("id")));

    Map<String, Object> row = service.getById("bp_order", TENANT_1, id1001, 1001L);
    assertEquals("1001", row.get("created_by"));
  }

  @Test
  @DisplayName("非本人不可见：page 空列表，getById 404（不暴露行存在性）")
  void nonOwner_gets404() {
    String id = createOrder("1001");

    var page = service.page("bp_order", TENANT_1, 1, 20, RuntimePageQuery.EMPTY, 2002L);
    assertEquals(0, page.getTotal());

    assertThrows(
        RuntimeRecordException.class,
        () -> service.getById("bp_order", TENANT_1, id, 2002L),
        "非本人详情应 404");
  }

  @Test
  @DisplayName("写路径同判：非本人 update/delete/restore 均 404")
  void nonOwner_writeRejected404() {
    String id = createOrder("1001");

    assertThrows(
        RuntimeRecordException.class,
        () -> service.update("bp_order", TENANT_1, id, Map.of("status", "REVIEWED"), null, "2002"),
        "非本人 update 应 404");
    assertThrows(
        RuntimeRecordException.class,
        () -> service.delete("bp_order", TENANT_1, id, null, "2002"),
        "非本人 delete 应 404");
    assertThrows(
        RuntimeRecordException.class,
        () -> service.restore("bp_order", TENANT_1, id, "2002"),
        "非本人 restore 应 404");
  }

  @Test
  @DisplayName("本人可写：update/delete 通过，restore 恢复")
  void owner_writeAllowed() {
    String id = createOrder("1001");

    Map<String, Object> updated =
        service.update("bp_order", TENANT_1, id, Map.of("status", "REVIEWED"), null, "1001");
    assertEquals("REVIEWED", updated.get("status"));

    service.delete("bp_order", TENANT_1, id, null, "1001");
    Map<String, Object> restored = service.restore("bp_order", TENANT_1, id, "1001");
    assertEquals("1001", restored.get("created_by"));
  }

  @Test
  @DisplayName("管理员豁免：operatorId 为 null 时不受行级 ACL 限制")
  void adminExemption_nullOperator() {
    String id = createOrder("1001");

    // null operatorId（管理员豁免）：非本人数据可读可写
    Map<String, Object> row = service.getById("bp_order", TENANT_1, id, null);
    assertEquals("1001", row.get("created_by"));

    var page = service.page("bp_order", TENANT_1, 1, 20, RuntimePageQuery.EMPTY, null);
    assertEquals(1, page.getTotal());

    service.update("bp_order", TENANT_1, id, Map.of("status", "ADMIN_FIX"), null, null);
    Map<String, Object> after = service.getById("bp_order", TENANT_1, id, null);
    assertEquals("ADMIN_FIX", after.get("status"));
  }

  @Test
  @DisplayName("内部调用豁免：无 ACL 参数的原版 page/getById 不受行级过滤影响")
  void internalCalls_unfiltered() {
    createOrder("1001");
    createOrder("2002");

    // 原版（无 rowOwnerId）＝动作执行/级联等内部调用形态：不受行级 ACL 拦截
    var page = service.page("bp_order", TENANT_1, 1, 20, RuntimePageQuery.EMPTY);
    assertEquals(2, page.getTotal());
    assertTrue(page.getRecords().stream().anyMatch(r -> "2002".equals(r.get("created_by"))));
  }
}
