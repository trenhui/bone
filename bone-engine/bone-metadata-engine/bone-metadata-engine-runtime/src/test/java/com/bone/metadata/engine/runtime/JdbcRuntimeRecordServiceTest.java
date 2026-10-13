package com.bone.metadata.engine.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
  private NamedParameterJdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    // 用 MySQL 兼容模式 + DATABASE_TO_LOWER，使 H2 接受生产代码的反引号语法并返回小写列标签，
    // 对齐生产 MySQL 语义（H2 默认模式既不支持反引号、又会将未加引标识符转大写，导致 queryForList
    // 返回的 Map key 为大写、与逻辑 code 不匹配）。
    var url = "jdbc:h2:mem:demo_order;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    var ds = new SimpleDriverDataSource(new Driver(), url);
    jdbc = new NamedParameterJdbcTemplate(ds);
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

  @Test
  void shouldRejectStateFieldDirectWriteOnCreateAndUpdate() {
    // G14：声明了 state_field 的实体，POST/PUT 直写状态字段必须 422（Runtime 设计 §4.5）
    var url = "jdbc:h2:mem:demo_state;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    var ds = new SimpleDriverDataSource(new Driver(), url);
    var jdbc = new NamedParameterJdbcTemplate(ds);
    jdbc.getJdbcTemplate().execute("DROP TABLE IF EXISTS demo_claim");
    jdbc.getJdbcTemplate()
        .execute(
            "CREATE TABLE demo_claim ("
                + "`id` BIGINT PRIMARY KEY, `tenant_id` BIGINT NOT NULL, "
                + "`title` VARCHAR(128) NOT NULL, `status` VARCHAR(32) NOT NULL, "
                + "`amount` DOUBLE, `version` INT NOT NULL DEFAULT 0, "
                + "`deleted` SMALLINT NOT NULL DEFAULT 0)");
    RuntimeEntityCatalog catalog =
        (code, tenantId) ->
            Optional.of(
                new PublishedRuntimeEntity(
                    "demo_claim",
                    "demo_claim",
                    "id",
                    tenantId,
                    List.of(
                        new RuntimeFieldColumn("id", "LONG", true, false, true),
                        new RuntimeFieldColumn("title", "STRING", true, false, false),
                        new RuntimeFieldColumn("status", "STRING", true, false, false),
                        new RuntimeFieldColumn("amount", "DOUBLE", false, false, false)),
                    "status"));
    JdbcRuntimeRecordService guard = new JdbcRuntimeRecordService(jdbc, catalog);

    RuntimeRecordException createEx =
        assertThrows(
            RuntimeRecordException.class,
            () ->
                guard.create(
                    "demo_claim", 1L, Map.of("title", "T", "status", "PAID", "amount", 88), 2001L));
    assertEquals(
        MetadataErrorCodes.RUNTIME_FIELD_PROTECTED, createEx.getErrorCode(), "POST 直写状态字段须 422");

    // 先以合法途径建一条（无状态字段的目录快照，模拟动作引擎已写入初始态）
    RuntimeEntityCatalog bareCatalog =
        (code, tenantId) ->
            Optional.of(
                new PublishedRuntimeEntity(
                    "demo_claim",
                    "demo_claim",
                    "id",
                    tenantId,
                    List.of(
                        new RuntimeFieldColumn("id", "LONG", true, false, true),
                        new RuntimeFieldColumn("title", "STRING", true, false, false),
                        new RuntimeFieldColumn("status", "STRING", true, false, false),
                        new RuntimeFieldColumn("amount", "DOUBLE", false, false, false)),
                    null));
    new JdbcRuntimeRecordService(jdbc, bareCatalog)
        .create("demo_claim", 1L, Map.of("title", "T2", "status", "DRAFT"), 2002L);

    RuntimeRecordException updateEx =
        assertThrows(
            RuntimeRecordException.class,
            () -> guard.update("demo_claim", 1L, "2002", Map.of("status", "PAID"), 0, "tester"));
    assertEquals(
        MetadataErrorCodes.RUNTIME_FIELD_PROTECTED, updateEx.getErrorCode(), "PUT 直写状态字段须 422");

    // 同一实体、不含状态字段的普通更新不受影响
    Map<String, Object> updated =
        guard.update("demo_claim", 1L, "2002", Map.of("amount", 128.50), 0, "tester");
    assertEquals(128.50, ((Number) updated.get("amount")).doubleValue());
  }

  @Test
  void shouldReturn404WhenPhysicalTableMissing() {
    // 已知 minor（非阻断）修复验证：实体的物理表缺失（已 DROP）时查询记录，
    // 必须返回干净 404（RUNTIME_ENTITY_NOT_FOUND），而非底层 SQL 500。
    // 注：H2 命名内存库在 SimpleDriverDataSource 多连接间不共享，故此处用「从未建表」的实体
    // 直接验证守卫逻辑（表不存在 → 404），与生产行为一致。
    var ghostUrl = "jdbc:h2:mem:demo_ghost;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    var ghostDs = new SimpleDriverDataSource(new Driver(), ghostUrl);
    var ghostJdbc = new NamedParameterJdbcTemplate(ghostDs);
    RuntimeEntityCatalog ghostCatalog =
        (code, tenantId) ->
            Optional.of(
                new PublishedRuntimeEntity(
                    "demo_ghost",
                    "demo_ghost",
                    "id",
                    tenantId,
                    List.of(
                        new RuntimeFieldColumn("id", "LONG", true, false, true),
                        new RuntimeFieldColumn("order_no", "STRING", true, false, false))));
    JdbcRuntimeRecordService ghostService = new JdbcRuntimeRecordService(ghostJdbc, ghostCatalog);

    RuntimeRecordException ex =
        assertThrows(
            RuntimeRecordException.class, () -> ghostService.page("demo_ghost", 1L, 1, 10));
    assertEquals(
        MetadataErrorCodes.RUNTIME_ENTITY_NOT_FOUND, ex.getErrorCode(), "物理表缺失时查询须返回 404 而非 500");
  }

  @Test
  void shouldInjectAutoNumberWhenFieldMissing() {
    // 自动编号（合理建议①）：实体声明 auto_number 后，创建记录缺省该字段时由平台生成单据号
    // （如 CLM-20261012-0001），替换原先 PAY-<nanoTime> 的无意义占位。
    var url = "jdbc:h2:mem:demo_an;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    var ds = new SimpleDriverDataSource(new Driver(), url);
    var anJdbc = new NamedParameterJdbcTemplate(ds);
    anJdbc.getJdbcTemplate().execute("DROP TABLE IF EXISTS demo_an");
    anJdbc.getJdbcTemplate().execute("DROP TABLE IF EXISTS meta_runtime_sequence");
    anJdbc
        .getJdbcTemplate()
        .execute(
            "CREATE TABLE demo_an ("
                + "`id` BIGINT PRIMARY KEY, `tenant_id` BIGINT NOT NULL, "
                + "`order_no` VARCHAR(64) NOT NULL, `amount` DOUBLE, `version` INT NOT NULL DEFAULT 0, "
                + "`deleted` SMALLINT NOT NULL DEFAULT 0)");
    anJdbc
        .getJdbcTemplate()
        .execute(
            "CREATE TABLE meta_runtime_sequence ("
                + "`seq_key` VARCHAR(200) NOT NULL, `current_value` BIGINT NOT NULL DEFAULT 0, "
                + "PRIMARY KEY (`seq_key`))");
    RuntimeEntityCatalog anCatalog =
        (code, tenantId) ->
            Optional.of(
                new PublishedRuntimeEntity(
                    "demo_an",
                    "demo_an",
                    "id",
                    tenantId,
                    List.of(
                        new RuntimeFieldColumn("id", "LONG", true, false, true),
                        new RuntimeFieldColumn("order_no", "STRING", true, true, false),
                        new RuntimeFieldColumn("amount", "DOUBLE", false, false, false)),
                    null,
                    null,
                    "{\"field\":\"order_no\",\"prefix\":\"CLM\",\"reset\":\"daily\"}"));
    JdbcRuntimeRecordService anService = new JdbcRuntimeRecordService(anJdbc, anCatalog);

    // 两次创建均不传 order_no → 平台注入，且序号单调递增
    Map<String, Object> r1 = anService.create("demo_an", 1L, Map.of("amount", 10), 9001L);
    Map<String, Object> r2 = anService.create("demo_an", 1L, Map.of("amount", 20), 9002L);
    String n1 = String.valueOf(r1.get("order_no"));
    String n2 = String.valueOf(r2.get("order_no"));
    String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    assertTrue(n1.matches("CLM-" + today + "-\\d{4}"), "首条单据号须为 CLM-<日期>-<4位序号>，实际: " + n1);
    assertTrue(n2.matches("CLM-" + today + "-\\d{4}"), "第二条单据号须为 CLM-<日期>-<4位序号>，实际: " + n2);
    assertNotEquals(n1, n2, "两条单据号必须不同（序号自增）");
  }

  @Test
  void shouldPreserveClientProvidedAutoNumberField() {
    // 负向探针：客户端显式传了该字段，平台不覆盖（避免破坏既有单据号流程）
    var url = "jdbc:h2:mem:demo_an2;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    var ds = new SimpleDriverDataSource(new Driver(), url);
    var anJdbc = new NamedParameterJdbcTemplate(ds);
    anJdbc.getJdbcTemplate().execute("DROP TABLE IF EXISTS demo_an2");
    anJdbc.getJdbcTemplate().execute("DROP TABLE IF EXISTS meta_runtime_sequence");
    anJdbc
        .getJdbcTemplate()
        .execute(
            "CREATE TABLE demo_an2 ("
                + "`id` BIGINT PRIMARY KEY, `tenant_id` BIGINT NOT NULL, "
                + "`order_no` VARCHAR(64) NOT NULL, `amount` DOUBLE, `version` INT NOT NULL DEFAULT 0, "
                + "`deleted` SMALLINT NOT NULL DEFAULT 0)");
    anJdbc
        .getJdbcTemplate()
        .execute(
            "CREATE TABLE meta_runtime_sequence ("
                + "`seq_key` VARCHAR(200) NOT NULL, `current_value` BIGINT NOT NULL DEFAULT 0, "
                + "PRIMARY KEY (`seq_key`))");
    RuntimeEntityCatalog anCatalog =
        (code, tenantId) ->
            Optional.of(
                new PublishedRuntimeEntity(
                    "demo_an2",
                    "demo_an2",
                    "id",
                    tenantId,
                    List.of(
                        new RuntimeFieldColumn("id", "LONG", true, false, true),
                        new RuntimeFieldColumn("order_no", "STRING", true, true, false),
                        new RuntimeFieldColumn("amount", "DOUBLE", false, false, false)),
                    null,
                    null,
                    "{\"field\":\"order_no\",\"prefix\":\"CLM\",\"reset\":\"daily\"}"));
    JdbcRuntimeRecordService anService = new JdbcRuntimeRecordService(anJdbc, anCatalog);

    Map<String, Object> created =
        anService.create("demo_an2", 1L, Map.of("order_no", "CUSTOM-001", "amount", 5), 9101L);
    assertEquals("CUSTOM-001", created.get("order_no"), "客户端显式单据号必须被保留");
  }
}
