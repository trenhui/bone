package com.bone.metadata.engine.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
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
 * 字段级角色裁剪的<b>端到端</b>测试（H2 真实执行，断言真实返回的键集合 / 真实写入拒绝）。
 *
 * <p>与 {@link RuntimeFieldVisibilityPolicyTest} 的分工：那个证策略判定逻辑，本类证<b>接线</b>—— 角色码从 service
 * 入口到响应键集合的整条路径真的通了，且写守卫真的拦在落库之前。
 *
 * <p><b>数据布局的判别性设计（防假绿）</b>：{@code title} 不配 roles、{@code salary} 限 {@code hr}、{@code
 * internal_note} 限 {@code admin}——「全裁」实现会红在 title 消失，「不裁」实现会红在 salary/internal_note 仍在。
 * 每个用例还带<b>反向对照</b>：同一请求换个有权限的角色必须放行，证明红灯不是「整体不返回」造成的。
 */
class RuntimeFieldVisibilityH2Test {

  private static final long TENANT_1 = 1L;
  private static final String HR = "hr";
  private static final String ADMIN = "admin";
  private static final String EMPLOYEE = "employee";

  private EmbeddedDatabase db;
  private NamedParameterJdbcTemplate jdbc;
  private final AtomicLong idGen = new AtomicLong(5000);

  private JdbcRuntimeRecordService service() {
    RuntimeEntityCatalog catalog =
        (code, tenantId) ->
            "bp_employee".equals(code)
                ? Optional.of(
                    new PublishedRuntimeEntity(
                        "bp_employee",
                        "bp_employee",
                        "id",
                        TENANT_1,
                        List.of(
                            new RuntimeFieldColumn("id", "LONG", false, false, true),
                            new RuntimeFieldColumn("title", "STRING", true, false, false),
                            new RuntimeFieldColumn("salary", "DECIMAL", false, false, false),
                            new RuntimeFieldColumn("internal_note", "STRING", false, false, false)),
                        null,
                        null,
                        null,
                        List.of(
                            new RuntimeFieldVisibility("salary", List.of(HR)),
                            new RuntimeFieldVisibility("internal_note", List.of(ADMIN)))))
                : Optional.empty();
    return new JdbcRuntimeRecordService(jdbc, catalog);
  }

  @BeforeEach
  void setUp() {
    db =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName("md_fieldvis;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
            .build();
    jdbc = new NamedParameterJdbcTemplate(db);
    jdbc.getJdbcTemplate()
        .execute(
            "CREATE TABLE `bp_employee` ("
                + "`id` BIGINT NOT NULL,"
                + "`tenant_id` BIGINT NOT NULL,"
                + "`title` VARCHAR(255),"
                + "`salary` DECIMAL(20,6),"
                + "`internal_note` VARCHAR(255),"
                + "`version` INT NOT NULL DEFAULT 0,"
                + "`deleted` SMALLINT NOT NULL DEFAULT 0,"
                + "`created_at` DATETIME,"
                + "`updated_at` DATETIME,"
                + "`created_by` VARCHAR(64),"
                + "`updated_by` VARCHAR(64),"
                + "PRIMARY KEY (`id`))");
  }

  @AfterEach
  void tearDown() {
    db.shutdown();
  }

  /** 直接 JDBC 写入以精确控制各列（走 create 会被写入校验/审计列注入干扰）。 */
  private String insertRow(String title, Object salary, String note) {
    long id = idGen.incrementAndGet();
    jdbc.getJdbcTemplate()
        .update(
            "INSERT INTO `bp_employee` (`id`,`tenant_id`,`title`,`salary`,`internal_note`,"
                + "`deleted`,`version`,`created_at`,`updated_at`,`created_by`) "
                + "VALUES (?,?,?,?,?,0,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'9001')",
            id,
            TENANT_1,
            title,
            salary,
            note);
    return String.valueOf(id);
  }

  private long countRows() {
    Long n =
        jdbc.getJdbcTemplate()
            .queryForObject("SELECT COUNT(*) FROM `bp_employee` WHERE tenant_id = 1", Long.class);
    return n == null ? 0 : n;
  }

  @Test
  @DisplayName("page：hr 见 salary 不见 internal_note；无角色两者皆裁；内部调用（null）不裁")
  void page_trimsByRole() {
    insertRow("张三", 50000, "试用期");
    JdbcRuntimeRecordService service = service();

    Map<String, Object> asHr =
        service
            .page("bp_employee", TENANT_1, 1, 50, RuntimePageQuery.EMPTY, null, List.of(HR))
            .getRecords()
            .get(0);
    assertTrue(asHr.containsKey("title"), "未配 roles 的字段必须对所有人可见（否则是全裁退化）");
    assertTrue(asHr.containsKey("salary"), "hr 应见 salary");
    assertFalse(asHr.containsKey("internal_note"), "hr 不应见 internal_note");

    Map<String, Object> asEmployee =
        service
            .page("bp_employee", TENANT_1, 1, 50, RuntimePageQuery.EMPTY, null, List.of(EMPLOYEE))
            .getRecords()
            .get(0);
    assertFalse(asEmployee.containsKey("salary"));
    assertFalse(asEmployee.containsKey("internal_note"));
    assertTrue(asEmployee.containsKey("title"));

    Map<String, Object> asNobody =
        service
            .page("bp_employee", TENANT_1, 1, 50, RuntimePageQuery.EMPTY, null, List.of())
            .getRecords()
            .get(0);
    assertFalse(asNobody.containsKey("salary"));
    assertFalse(asNobody.containsKey("internal_note"));
    assertTrue(asNobody.containsKey("title"), "空角色也不能把公共字段裁掉");

    Map<String, Object> asInternal =
        service
            .page("bp_employee", TENANT_1, 1, 50, RuntimePageQuery.EMPTY, null, null)
            .getRecords()
            .get(0);
    assertTrue(asInternal.containsKey("salary"), "内部调用（roleCodes=null）不裁剪——受信路径契约");
    assertTrue(asInternal.containsKey("internal_note"));
  }

  @Test
  @DisplayName("getById：同一套裁剪（反向对照：admin 两者皆见）")
  void getById_trimsByRole() {
    String id = insertRow("李四", 60000, "核心");
    JdbcRuntimeRecordService service = service();

    Map<String, Object> asHr = service.getById("bp_employee", TENANT_1, id, null, List.of(HR));
    assertTrue(asHr.containsKey("salary"));
    assertFalse(asHr.containsKey("internal_note"));

    Map<String, Object> asAdmin =
        service.getById("bp_employee", TENANT_1, id, null, List.of(ADMIN));
    // 反向对照：admin 有 internal_note 的权限、但没有 hr 角色——仍不应见 salary。
    // 「有权限的角色能见、无权限的字段仍裁」才证明上一条不是整体不返回。
    assertTrue(asAdmin.containsKey("internal_note"), "admin 应见 internal_note");
    assertFalse(asAdmin.containsKey("salary"), "admin 无 hr 角色，仍不应见 salary");
  }

  @Test
  @DisplayName("create：请求体含角色不可见字段 → 403 语义错误码且不落库（fail-closed）")
  void create_rejectsInvisibleFieldAndDoesNotInsert() {
    JdbcRuntimeRecordService service = service();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("title", "王五");
    body.put("salary", 70000);

    RuntimeRecordException ex =
        assertThrows(
            RuntimeRecordException.class,
            () -> service.create("bp_employee", TENANT_1, body, 9999L, "7001", List.of(EMPLOYEE)));
    assertEquals(MetadataErrorCodes.RUNTIME_FIELD_NOT_VISIBLE, ex.getErrorCode());
    assertEquals(0, countRows(), "被拒绝的创建不得落库（静默 drop 会让调用方误以为已保存）");
  }

  @Test
  @DisplayName("create：角色命中 → 落库成功且响应同样裁剪（创建者也拿不到不可见字段回显）")
  void create_allowsVisibleRole_andTrimsResponse() {
    JdbcRuntimeRecordService service = service();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("title", "赵六");
    body.put("salary", 80000);

    Map<String, Object> created =
        service.create("bp_employee", TENANT_1, body, 9998L, "7002", List.of(HR));
    assertEquals(1, countRows());
    assertEquals("赵六", created.get("title"));
    assertTrue(created.containsKey("salary"), "hr 的创建响应应含 salary");
    assertFalse(created.containsKey("internal_note"), "未提交的字段本就不在，但若实现误放行需此断言兜底");
  }

  @Test
  @DisplayName("update：含不可见字段 → 拒绝；仅改可见字段 → 成功且响应裁剪")
  void update_guardAndTrim() {
    String id = insertRow("孙七", 90000, "备注");
    JdbcRuntimeRecordService service = service();

    Map<String, Object> bad = new LinkedHashMap<>();
    bad.put("internal_note", "篡改");
    RuntimeRecordException ex =
        assertThrows(
            RuntimeRecordException.class,
            () -> service.update("bp_employee", TENANT_1, id, bad, null, "7003", List.of(HR)));
    assertEquals(MetadataErrorCodes.RUNTIME_FIELD_NOT_VISIBLE, ex.getErrorCode());
    // 原值未被改动
    Map<String, Object> after = service.getById("bp_employee", TENANT_1, id, null, List.of(ADMIN));
    assertEquals("备注", after.get("internal_note"), "被拒绝的更新不得改库");

    Map<String, Object> good = new LinkedHashMap<>();
    good.put("title", "孙七-改");
    // 操作者必须是行属主（created_by='9001'）：写路径的行级 ACL 先于字段守卫之后生效，
    // 用非属主 ID 会撞 404，测的就不是字段裁剪了
    Map<String, Object> updated =
        service.update("bp_employee", TENANT_1, id, good, null, "9001", List.of(HR));
    assertEquals("孙七-改", updated.get("title"));
    assertTrue(updated.containsKey("salary"));
    assertFalse(updated.containsKey("internal_note"));
  }

  @Test
  @DisplayName("pageDeleted：回收站与数据列表同一套裁剪（不能一个漏一个）")
  void pageDeleted_trimsToo() {
    String id = insertRow("周八", 10000, "归档备注");
    jdbc.getJdbcTemplate()
        .update("UPDATE `bp_employee` SET `deleted` = 1 WHERE `id` = ?", Long.valueOf(id));
    JdbcRuntimeRecordService service = service();

    Map<String, Object> asEmployee =
        service
            .pageDeleted("bp_employee", TENANT_1, 1, 50, RuntimePageQuery.EMPTY, List.of(EMPLOYEE))
            .getRecords()
            .get(0);
    assertTrue(asEmployee.containsKey("title"));
    assertFalse(asEmployee.containsKey("salary"));
    assertFalse(asEmployee.containsKey("internal_note"));

    Map<String, Object> asAdmin =
        service
            .pageDeleted("bp_employee", TENANT_1, 1, 50, RuntimePageQuery.EMPTY, List.of(ADMIN))
            .getRecords()
            .get(0);
    assertTrue(asAdmin.containsKey("internal_note"), "反向对照：admin 在回收站也见全量");
  }

  @Test
  @DisplayName("restore：恢复响应同样按角色裁剪")
  void restore_trimsResponse() {
    String id = insertRow("吴九", 11000, "恢复备注");
    jdbc.getJdbcTemplate()
        .update("UPDATE `bp_employee` SET `deleted` = 1 WHERE `id` = ?", Long.valueOf(id));
    JdbcRuntimeRecordService service = service();

    Map<String, Object> restored =
        service.restore("bp_employee", TENANT_1, id, "9001", List.of(HR));
    assertTrue(restored.containsKey("title"));
    assertTrue(restored.containsKey("salary"));
    assertFalse(restored.containsKey("internal_note"));
  }
}
