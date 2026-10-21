package com.bone.metadata.engine.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.h2.Driver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

/**
 * 写前置钩子（{@link RuntimeWriteHook}，Directus Filter 分层）守门测试。
 *
 * <p>覆盖四条路径：① 匹配实体的 beforeCreate 可改写载荷；② 匹配实体的 beforeUpdate 可阻断 （抛 {@code
 * META_RUNTIME_VALIDATION_FAILED}）；③ 未涉及敏感字段的更新放行；④ 未匹配实体不触发。
 */
class RuntimeWriteHookTest {

  private JdbcRuntimeRecordService service;
  private NamedParameterJdbcTemplate jdbc;

  /** 演示钩子：报销单在 APPROVED（含）之后不允许改 amount（与 ExpenseReimbursementWriteHook 同规则）。 */
  static class FrozenAmountHook implements RuntimeWriteHook {
    final Map<String, Object> observed = new java.util.HashMap<>();
    private final NamedParameterJdbcTemplate jdbc;

    FrozenAmountHook(NamedParameterJdbcTemplate jdbc) {
      this.jdbc = jdbc;
    }

    @Override
    public String entityCode() {
      return "expense_reimbursement";
    }

    @Override
    public void beforeCreate(RuntimeWriteHookContext ctx) {
      // 改写演示：创建时把 memo 缺省值补上（Directus filter 可改载荷）
      ctx.payload().putIfAbsent("memo", "default-memo");
    }

    @Override
    public void beforeUpdate(RuntimeWriteHookContext ctx) {
      observed.put("recordId", ctx.recordId());
      if (!ctx.payload().containsKey("amount")) {
        return;
      }
      String status = currentStatus(ctx);
      if ("APPROVED".equals(status) || "PAID".equals(status)) {
        throw new RuntimeRecordException(
            MetadataErrorCodes.RUNTIME_VALIDATION_FAILED, "报销单状态为 " + status + "，不允许修改金额");
      }
    }

    private String currentStatus(RuntimeWriteHookContext ctx) {
      String sql =
          "SELECT `status` FROM "
              + ctx.entity().physicalTableName()
              + " WHERE `id` = :id AND `deleted` = 0";
      try {
        return jdbc.queryForObject(
            sql,
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource(
                "id", ctx.recordId()),
            String.class);
      } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
        return null;
      }
    }
  }

  @BeforeEach
  void setUp() {
    var url = "jdbc:h2:mem:write_hook;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    var ds = new SimpleDriverDataSource(new Driver(), url);
    jdbc = new NamedParameterJdbcTemplate(ds);
    jdbc.getJdbcTemplate().execute("DROP TABLE IF EXISTS expense_reimbursement");
    jdbc.getJdbcTemplate()
        .execute(
            "CREATE TABLE expense_reimbursement ("
                + "`id` BIGINT PRIMARY KEY, `tenant_id` BIGINT NOT NULL, "
                + "`title` VARCHAR(64), `amount` DOUBLE, `status` VARCHAR(16), `memo` VARCHAR(64), "
                + "`version` INT NOT NULL DEFAULT 0, `deleted` SMALLINT NOT NULL DEFAULT 0)");
    RuntimeEntityCatalog catalog =
        (code, tenantId) -> {
          if (!"expense_reimbursement".equals(code)) {
            return Optional.empty();
          }
          return Optional.of(
              new PublishedRuntimeEntity(
                  "expense_reimbursement",
                  "expense_reimbursement",
                  "id",
                  tenantId,
                  List.of(
                      new RuntimeFieldColumn("id", "LONG", true, false, true),
                      new RuntimeFieldColumn("title", "STRING", false, false, false),
                      new RuntimeFieldColumn("amount", "DOUBLE", false, false, false),
                      new RuntimeFieldColumn("status", "STRING", false, false, false),
                      new RuntimeFieldColumn("memo", "STRING", false, false, false))));
        };
    service =
        new JdbcRuntimeRecordService(
            jdbc,
            catalog,
            RuntimeAuditSink.NOOP,
            RuntimeStateEntryResolver.NONE,
            null,
            List.of(new FrozenAmountHook(jdbc)));
  }

  @Test
  @DisplayName("beforeCreate 钩子可改写载荷（memo 缺省值补全）")
  void beforeCreateCanMutatePayload() {
    Map<String, Object> created =
        service.create(
            "expense_reimbursement",
            1L,
            Map.of("title", "打车", "amount", 80.0, "status", "DRAFT"),
            1L);
    assertEquals("default-memo", created.get("memo"), "钩子应补全 memo 缺省值");
  }

  @Test
  @DisplayName("beforeUpdate 钩子阻断：APPROVED 后改 amount 抛 422")
  void beforeUpdateBlocksFrozenAmount() {
    service.create(
        "expense_reimbursement", 1L, Map.of("title", "打车", "amount", 80.0, "status", "DRAFT"), 1L);
    jdbc.getJdbcTemplate()
        .update("UPDATE expense_reimbursement SET `status` = 'APPROVED' WHERE `id` = 1");

    RuntimeRecordException ex =
        assertThrows(
            RuntimeRecordException.class,
            () -> service.update("expense_reimbursement", 1L, "1", Map.of("amount", 999.0)));
    assertEquals(
        MetadataErrorCodes.RUNTIME_VALIDATION_FAILED, ex.getErrorCode(), "审批链上改金额必须收敛到校验失败码");
    assertEquals("1", ((FrozenAmountHook) serviceWriteHooks().get(0)).observed.get("recordId"));
  }

  @Test
  @DisplayName("beforeUpdate 钩子放行：DRAFT 改金额不阻断")
  void beforeUpdateAllowsDraft() {
    service.create(
        "expense_reimbursement", 1L, Map.of("title", "打车", "amount", 80.0, "status", "DRAFT"), 1L);
    Map<String, Object> updated =
        service.update("expense_reimbursement", 1L, "1", Map.of("amount", 120.0));
    assertEquals(120.0, ((Number) updated.get("amount")).doubleValue(), 0.0001);
  }

  @Test
  @DisplayName("未匹配实体不触发钩子")
  void unmatchedEntityDoesNotInvokeHook() {
    // 无 biz_order 实体的钩子 → 不应触发（构造不同实体验证 hooks 列表为空路径）
    var url = "jdbc:h2:mem:write_hook2;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    var ds = new SimpleDriverDataSource(new Driver(), url);
    var jdbc2 = new NamedParameterJdbcTemplate(ds);
    jdbc2.getJdbcTemplate().execute("DROP TABLE IF EXISTS biz_order");
    jdbc2
        .getJdbcTemplate()
        .execute(
            "CREATE TABLE biz_order ("
                + "`id` BIGINT PRIMARY KEY, `tenant_id` BIGINT NOT NULL, "
                + "`name` VARCHAR(64), `version` INT NOT NULL DEFAULT 0, `deleted` SMALLINT NOT NULL DEFAULT 0)");
    RuntimeEntityCatalog catalog =
        (code, tenantId) ->
            Optional.of(
                new PublishedRuntimeEntity(
                    "biz_order",
                    "biz_order",
                    "id",
                    tenantId,
                    List.of(new RuntimeFieldColumn("name", "STRING", false, false, false))));
    var svc =
        new JdbcRuntimeRecordService(
            jdbc2,
            catalog,
            RuntimeAuditSink.NOOP,
            RuntimeStateEntryResolver.NONE,
            null,
            List.of(new FrozenAmountHook(jdbc2)));
    svc.create("biz_order", 1L, Map.of("name", "x"), 9L);
    svc.update("biz_order", 1L, "9", Map.of("name", "y"));
    assertEquals("y", svc.getById("biz_order", 1L, "9").get("name"));
  }

  /** 反射取 service 的 hooks 列表（测试断言钩子被调用）。 */
  @SuppressWarnings("unchecked")
  private List<RuntimeWriteHook> serviceWriteHooks() {
    try {
      var f = JdbcRuntimeRecordService.class.getDeclaredField("writeHooks");
      f.setAccessible(true);
      return (List<RuntimeWriteHook>) f.get(service);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
