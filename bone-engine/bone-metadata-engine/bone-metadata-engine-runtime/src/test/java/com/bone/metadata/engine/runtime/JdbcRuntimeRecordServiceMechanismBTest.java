package com.bone.metadata.engine.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.h2.Driver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

/**
 * 机制 B 联调契约验证：逻辑 code 与物理 ext_* 列的解耦映射，必须在 写入(create/update) / 读取(getById/page) / 唯一校验 / 排序 / 过滤
 * 各路径保持一致。这是「前端按逻辑 code 读写」与「运行时数据 API 按物理 ext_* 列存取」之间的契约边界，属于前后端联调的核心链路（机制 A 物理列即 code 的路径由
 * {@link JdbcRuntimeRecordServiceTest} 覆盖）。
 */
class JdbcRuntimeRecordServiceMechanismBTest {

  private JdbcRuntimeRecordService service;
  private NamedParameterJdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    // 用 MySQL 兼容模式 + DATABASE_TO_LOWER，使 H2 接受生产代码的反引号语法并返回小写列标签，
    // 对齐生产 MySQL 语义（H2 默认模式既不支持反引号、又会将未加引标识符转大写，导致 queryForList
    // 返回的 Map key 为大写、与逻辑 code 不匹配）。
    var url = "jdbc:h2:mem:biz_order;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    var ds = new SimpleDriverDataSource(new Driver(), url);
    jdbc = new NamedParameterJdbcTemplate(ds);
    jdbc.getJdbcTemplate().execute("DROP TABLE IF EXISTS biz_order");
    jdbc.getJdbcTemplate()
        .execute(
            "CREATE TABLE biz_order ("
                + "`id` BIGINT PRIMARY KEY, `tenant_id` BIGINT NOT NULL, "
                + "`order_no` VARCHAR(64) NOT NULL, `ext_1` VARCHAR(64), `ext_2` DOUBLE, `ext_3` VARCHAR(64), "
                + "`version` INT NOT NULL DEFAULT 0, `deleted` SMALLINT NOT NULL DEFAULT 0)");
    RuntimeEntityCatalog catalog =
        (code, tenantId) -> {
          if (!"biz_order".equals(code)) {
            return Optional.empty();
          }
          return Optional.of(
              new PublishedRuntimeEntity(
                  "biz_order",
                  "biz_order",
                  "id",
                  tenantId,
                  List.of(
                      new RuntimeFieldColumn("id", "LONG", true, false, true),
                      new RuntimeFieldColumn("order_no", "STRING", true, true, false),
                      new RuntimeFieldColumn(
                          "receiver_name", "STRING", false, false, false, "ext_1"),
                      new RuntimeFieldColumn("ext_amount", "DOUBLE", false, false, false, "ext_2"),
                      new RuntimeFieldColumn("vip_code", "STRING", false, true, false, "ext_3"))));
        };
    service = new JdbcRuntimeRecordService(jdbc, catalog);
  }

  @Test
  void createWritesToPhysicalExtColumnsButReturnsLogicalKeys() {
    Map<String, Object> created =
        service.create(
            "biz_order",
            1L,
            Map.of(
                "order_no", "O-1",
                "receiver_name", "张三",
                "ext_amount", 99.5,
                "vip_code", "V1"),
            1001L);

    // 返回给前端的必须是逻辑 code，而非物理 ext_* 列名
    assertEquals("张三", created.get("receiver_name"));
    assertEquals(99.5, ((Number) created.get("ext_amount")).doubleValue(), 0.0001);
    assertEquals("V1", created.get("vip_code"));
    assertEquals("O-1", created.get("order_no"));
    assertFalse(created.containsKey("ext_1"), "不应向调用方暴露物理 ext_* 列名");
    assertFalse(created.containsKey("ext_2"));
    assertFalse(created.containsKey("ext_3"));

    // 物理落库必须写入 ext_* 预留列（别名保证返回 Map key 为小写逻辑名）
    Map<String, Object> raw =
        jdbc.getJdbcTemplate()
            .queryForMap(
                "SELECT \"ext_1\" AS ext_1, \"ext_2\" AS ext_2, \"ext_3\" AS ext_3 FROM biz_order WHERE \"id\" = 1001");
    assertEquals("张三", raw.get("ext_1"));
    assertEquals(99.5, ((Number) raw.get("ext_2")).doubleValue(), 0.0001);
    assertEquals("V1", raw.get("ext_3"));
  }

  @Test
  void getByIdReturnsLogicalKeys() {
    service.create(
        "biz_order", 1L, Map.of("order_no", "O-2", "receiver_name", "李四", "vip_code", "V2"), 1002L);
    Map<String, Object> loaded = service.getById("biz_order", 1L, "1002");
    assertEquals("李四", loaded.get("receiver_name"));
    assertEquals("V2", loaded.get("vip_code"));
    assertFalse(loaded.containsKey("ext_1"));
  }

  @Test
  void pageReturnsLogicalKeys() {
    service.create("biz_order", 1L, Map.of("order_no", "A", "receiver_name", "甲"), 1L);
    service.create("biz_order", 1L, Map.of("order_no", "B", "receiver_name", "乙"), 2L);
    var page = service.page("biz_order", 1L, 1, 10);
    assertEquals(2, page.getRecords().size());
    assertTrue(page.getRecords().stream().noneMatch(r -> r.containsKey("ext_1")));
    assertTrue(page.getRecords().stream().allMatch(r -> r.containsKey("receiver_name")));
  }

  @Test
  void uniqueCheckTargetsPhysicalColumnMechanismB() {
    service.create("biz_order", 1L, Map.of("order_no", "U1", "vip_code", "DUP-V"), 10L);
    assertThrows(
        RuntimeRecordException.class,
        () -> service.create("biz_order", 1L, Map.of("order_no", "U2", "vip_code", "DUP-V"), 11L));
  }

  @Test
  void filterOnMechanismBField() {
    service.create("biz_order", 1L, Map.of("order_no", "F1", "vip_code", "MATCH"), 20L);
    service.create("biz_order", 1L, Map.of("order_no", "F2", "vip_code", "OTHER"), 21L);
    var query = RuntimePageQuery.parse(null, null, "vip_code:MATCH");
    var page = service.page("biz_order", 1L, 1, 10, query);
    assertEquals(1, page.getRecords().size());
    assertEquals("MATCH", page.getRecords().get(0).get("vip_code"));
  }

  @Test
  void sortOnMechanismBField() {
    service.create("biz_order", 1L, Map.of("order_no", "S1", "receiver_name", "Zeta"), 30L);
    service.create("biz_order", 1L, Map.of("order_no", "S2", "receiver_name", "Alpha"), 31L);
    var query = RuntimePageQuery.parse(null, "receiver_name", null);
    var page = service.page("biz_order", 1L, 1, 10, query);
    assertEquals(2, page.getRecords().size());
    // 升序：Alpha 应在前
    assertEquals("Alpha", page.getRecords().get(0).get("receiver_name"));
    assertEquals("Zeta", page.getRecords().get(1).get("receiver_name"));
  }

  @Test
  void updateWritesToPhysicalExtColumn() {
    service.create("biz_order", 1L, Map.of("order_no", "UP1", "receiver_name", "旧名"), 40L);
    service.update("biz_order", 1L, "40", Map.of("receiver_name", "新名"));
    Map<String, Object> loaded = service.getById("biz_order", 1L, "40");
    assertEquals("新名", loaded.get("receiver_name"));
    Map<String, Object> raw =
        jdbc.getJdbcTemplate()
            .queryForMap("SELECT \"ext_1\" AS ext_1 FROM biz_order WHERE \"id\" = 40");
    assertEquals("新名", raw.get("ext_1"));
  }
}
