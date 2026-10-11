package com.bone.metadata.engine.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
 * 行策略（Runtime §4.7.1 v0.48 · 对标 Supabase RLS「策略即 WHERE」多规则 OR 组合）。
 *
 * <p>实体声明 row_policies（meta_entity.row_policies JSON）：
 *
 * <pre>{@code
 * {"owner_field":"created_by",
 *  "rules":[{"name":"owner_or_approver","effect":"allow",
 *            "when":"created_by = :operator OR approver_id = :operator"}]}
 * }</pre>
 *
 * <p>语义：本人（created_by）与审批人（approver_id）均可见/可写，其余 404（默认拒绝）； 未知列规则被忽略（保守回退 owner 单规则）；when
 * 仅白名单原子比较，注入被拒绝。
 */
@DisplayName("行策略（RLS 多规则 OR 组合）· 本人+审批人可见，其余 404，注入拒绝")
class RuntimeRowPolicyTest {

  private static final long TENANT_1 = 1L;
  private static final String POLICY_OWNER_APPROVER =
      "{\"owner_field\":\"created_by\",\"rules\":["
          + "{\"name\":\"owner_or_approver\",\"effect\":\"allow\","
          + "\"when\":\"created_by = :operator OR approver_id = :operator\"}]}";

  private EmbeddedDatabase db;
  private NamedParameterJdbcTemplate jdbc;
  private RuntimeEntityCatalog catalog;
  private JdbcRuntimeRecordService service;
  private final AtomicLong idGen = new AtomicLong(5000);

  @BeforeEach
  void setUp() {
    db =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName("md_row_policy;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
            .build();
    jdbc = new NamedParameterJdbcTemplate(db);
    jdbc.getJdbcTemplate()
        .execute(
            "CREATE TABLE `bp_expense` ("
                + "`id` BIGINT NOT NULL,"
                + "`tenant_id` BIGINT NOT NULL,"
                + "`title` VARCHAR(255),"
                + "`amount` DECIMAL(20,6),"
                + "`approver_id` BIGINT,"
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
            "bp_expense".equals(code)
                ? Optional.of(
                    new PublishedRuntimeEntity(
                        "bp_expense",
                        "bp_expense",
                        "id",
                        TENANT_1,
                        List.of(
                            new RuntimeFieldColumn("id", "LONG", false, false, true),
                            new RuntimeFieldColumn("title", "STRING", true, false, false),
                            new RuntimeFieldColumn("amount", "DECIMAL", true, false, false),
                            new RuntimeFieldColumn("approver_id", "LONG", false, false, false),
                            new RuntimeFieldColumn("status", "STRING", true, false, false)),
                        null,
                        POLICY_OWNER_APPROVER))
                : Optional.empty();
    service = new JdbcRuntimeRecordService(jdbc, catalog);
  }

  @AfterEach
  void tearDown() {
    if (db != null) {
      db.shutdown();
    }
  }

  private String createExpense(String ownerId, Long approverId) {
    Map<String, Object> payload = new java.util.HashMap<>();
    payload.put("title", "差旅");
    payload.put("amount", 320.5d);
    payload.put("status", "NEW");
    if (approverId != null) {
      payload.put("approver_id", approverId);
    }
    Map<String, Object> created =
        service.create("bp_expense", TENANT_1, payload, idGen.incrementAndGet(), ownerId);
    return String.valueOf(created.get("id"));
  }

  @Test
  @DisplayName("审批人可见他人单：page 含该行，getById 可读（策略 OR 命中）")
  void approver_seesOthersRow_viaPolicy() {
    String id = createExpense("1001", 3003L);

    var page = service.page("bp_expense", TENANT_1, 1, 20, RuntimePageQuery.EMPTY, 3003L);
    assertEquals(1, page.getTotal());
    assertEquals(id, String.valueOf(page.getRecords().get(0).get("id")));

    Map<String, Object> row = service.getById("bp_expense", TENANT_1, id, 3003L);
    assertEquals("1001", row.get("created_by"));
    assertEquals("3003", String.valueOf(row.get("approver_id")));
  }

  @Test
  @DisplayName("本人可见：owner 规则独立命中（无 approver 也可见）")
  void owner_stillSeesOwnRows() {
    String id = createExpense("1001", null);

    var page = service.page("bp_expense", TENANT_1, 1, 20, RuntimePageQuery.EMPTY, 1001L);
    assertEquals(1, page.getTotal());
    assertEquals(id, String.valueOf(page.getRecords().get(0).get("id")));
  }

  @Test
  @DisplayName("非本人非审批人：page 空列表，getById/update 404（默认拒绝）")
  void stranger_404_evenIfRowExists() {
    String id = createExpense("1001", 3003L);

    var page = service.page("bp_expense", TENANT_1, 1, 20, RuntimePageQuery.EMPTY, 9009L);
    assertEquals(0, page.getTotal());

    assertThrows(
        RuntimeRecordException.class,
        () -> service.getById("bp_expense", TENANT_1, id, 9009L),
        "陌生人详情应 404");
    assertThrows(
        RuntimeRecordException.class,
        () -> service.update("bp_expense", TENANT_1, id, Map.of("title", "被篡改"), null, "9009"),
        "陌生人改单应 404");
  }

  @Test
  @DisplayName("审批人写他人单允许（策略命中 = 可见可写，RLS 一致性）")
  void approver_canWriteOthersRow_viaPolicy() {
    String id = createExpense("1001", 3003L);

    service.update("bp_expense", TENANT_1, id, Map.of("title", "审批人补充备注"), null, "3003");
    Map<String, Object> row = service.getById("bp_expense", TENANT_1, id, 3003L);
    assertEquals("审批人补充备注", row.get("title"));
    assertEquals("3003", row.get("updated_by"));
  }

  @Test
  @DisplayName("未知列规则被忽略 + 有效规则（OR）仍生效：本人/审批人可见，陌生人 404")
  void unknownColumnRule_ignored_validRuleStillWorks() {
    String json =
        "{\"owner_field\":\"created_by\",\"rules\":["
            + "{\"name\":\"bad_rule\",\"effect\":\"allow\","
            + "\"when\":\"manager_id = :operator\"},"
            + "{\"name\":\"owner_or_approver\",\"effect\":\"allow\","
            + "\"when\":\"created_by = :operator OR approver_id = :operator\"}]}";
    String id = createExpenseWithPolicy("1001", 3003L, json);

    // 未知列规则被忽略；owner_or_approver 生效 → 本人可见
    Map<String, Object> row = service.getById("bp_expense", TENANT_1, id, 1001L);
    assertEquals("1001", row.get("created_by"));
    // 审批人（approver_id 命中）仍可见 —— 未知列规则丢弃不影响有效规则
    Map<String, Object> asApprover = service.getById("bp_expense", TENANT_1, id, 3003L);
    assertEquals("1001", asApprover.get("created_by"));
    // 陌生人仍 404
    assertThrows(
        RuntimeRecordException.class,
        () -> service.getById("bp_expense", TENANT_1, id, 9009L),
        "陌生人应 404");
  }

  @Test
  @DisplayName("注入拒绝：when 含任意 SQL（分号/字面量）整条策略被忽略，回退 owner 默认")
  void injectionAttempt_rejected_fallbackToOwner() {
    String json =
        "{\"owner_field\":\"created_by\",\"rules\":["
            + "{\"name\":\"evil\",\"effect\":\"allow\","
            + "\"when\":\"created_by = :operator OR 1=1\"}]}";
    String id = createExpenseWithPolicy("1001", 3003L, json);

    // 非法 when（含 1=1 字面量）整条被忽略 → 回退 owner 默认：陌生人仍 404（未被 1=1 绕过）
    assertThrows(
        RuntimeRecordException.class,
        () -> service.getById("bp_expense", TENANT_1, id, 9009L),
        "注入不应绕过行级 ACL");
    // 本人仍可见
    assertEquals("1001", service.getById("bp_expense", TENANT_1, id, 1001L).get("created_by"));
  }

  private String createExpenseWithPolicy(String ownerId, Long approverId, String policyJson) {
    return createExpenseWithPolicyAndCatalog(ownerId, approverId, policyJson);
  }

  private String createExpenseWithPolicyAndCatalog(
      String ownerId, Long approverId, String policyJson) {
    // 重建 catalog 注入自定义策略（按用例隔离）
    catalog =
        (code, tenantId) ->
            "bp_expense".equals(code)
                ? Optional.of(
                    new PublishedRuntimeEntity(
                        "bp_expense",
                        "bp_expense",
                        "id",
                        TENANT_1,
                        List.of(
                            new RuntimeFieldColumn("id", "LONG", false, false, true),
                            new RuntimeFieldColumn("title", "STRING", true, false, false),
                            new RuntimeFieldColumn("amount", "DECIMAL", true, false, false),
                            new RuntimeFieldColumn("approver_id", "LONG", false, false, false),
                            new RuntimeFieldColumn("status", "STRING", true, false, false)),
                        null,
                        policyJson))
                : Optional.empty();
    service = new JdbcRuntimeRecordService(jdbc, catalog);
    return createExpense(ownerId, approverId);
  }

  // ============ v0.50：子表旁路防御（assertRowOwner 公开复用契约） ============
  // 子表端点（listChildren/replaceChildren）前置调用主行行级 ACL：子表可见性由父行控制，
  // 防「知道父行 id 即可枚举/替换任意明细」。此处直接验证 public 断言契约。

  @Test
  @DisplayName("子表旁路防御：陌生人调 assertRowOwner 查他人单据 → 404（列表/替换明细前置断言的基石）")
  void childEndpoint_stranger_404() {
    String id = createExpense("1001", 3003L);
    PublishedRuntimeEntity parent =
        catalog.findPublishedRuntime("bp_expense", TENANT_1).orElseThrow();
    assertThrows(
        RuntimeRecordException.class,
        () -> service.assertRowOwner(parent, TENANT_1, id, "9009"),
        "陌生人访问他人单据明细应 404（不泄露行存在性）");
  }

  @Test
  @DisplayName("子表旁路防御：审批人（approver_id 命中）可访问他人单据明细")
  void childEndpoint_approver_allowed() {
    String id = createExpense("1001", 3003L);
    PublishedRuntimeEntity parent =
        catalog.findPublishedRuntime("bp_expense", TENANT_1).orElseThrow();
    assertDoesNotThrow(() -> service.assertRowOwner(parent, TENANT_1, id, "3003"));
  }

  @Test
  @DisplayName("子表旁路防御：本人可访问自己的单据明细")
  void childEndpoint_owner_allowed() {
    String id = createExpense("1001", 3003L);
    PublishedRuntimeEntity parent =
        catalog.findPublishedRuntime("bp_expense", TENANT_1).orElseThrow();
    assertDoesNotThrow(() -> service.assertRowOwner(parent, TENANT_1, id, "1001"));
  }

  @Test
  @DisplayName("子表旁路防御：管理员（null）/ system 回调豁免明细访问")
  void childEndpoint_adminSystem_exempt() {
    String id = createExpense("1001", 3003L);
    PublishedRuntimeEntity parent =
        catalog.findPublishedRuntime("bp_expense", TENANT_1).orElseThrow();
    assertDoesNotThrow(() -> service.assertRowOwner(parent, TENANT_1, id, null));
    assertDoesNotThrow(() -> service.assertRowOwner(parent, TENANT_1, id, "system"));
  }
}
