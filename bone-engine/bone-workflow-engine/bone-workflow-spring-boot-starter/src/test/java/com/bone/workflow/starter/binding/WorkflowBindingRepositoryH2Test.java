package com.bone.workflow.starter.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.starter.binding.WorkflowBindingRepository.WorkflowBinding;
import com.bone.workflow.starter.sdk.WorkflowSdkTestContext;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 实体 → 流程绑定（{@code wf_binding}）访问组件的 H2 端到端测试。
 *
 * <p><b>判别性设计（防假绿）</b>：四个过滤维度（{@code enabled}/{@code deleted}/{@code tenant_id}/ 无上下文
 * fail-closed）各有负向用例——单租户happy-path 测试完全看不出查询漏写 {@code tenant_id} 谓词； {@link
 * #duplicateActiveBindings_failFastRatherThanPickOne()} 钉死「多条生效绑定必须炸错」——静默取第一条
 * 会让运营以为配了新流程、实际走的还是旧流程，且从外部观察不到任何异常。
 */
class WorkflowBindingRepositoryH2Test {

  private static final long TENANT = 1L;
  private static final long OTHER_TENANT = 2L;
  private static final String ENTITY = "expense_reimbursement";

  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private WorkflowBindingRepository repository;

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(TENANT);
    context = WorkflowSdkTestContext.create();
    jdbc = context.getBean(JdbcTemplate.class);
    repository = context.getBean(WorkflowBindingRepository.class);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    if (context != null) {
      context.close();
    }
  }

  @Test
  @DisplayName("命中：配了生效绑定必须能解析出 defKey 与 startAction")
  void bindingHit_resolvesDefKeyAndStartAction() {
    givenBinding("B-1", TENANT, ENTITY, "expense", "submit", 1, 0);

    Optional<WorkflowBinding> binding = repository.findByEntityCode(ENTITY);

    assertTrue(binding.isPresent(), "配了生效绑定必须能解析出来");
    assertEquals(ENTITY, binding.get().entityCode());
    assertEquals("expense", binding.get().defKey());
    assertEquals("submit", binding.get().startAction());
  }

  @Test
  @DisplayName("未命中：没配绑定返回 empty（调用方据此 fail-fast，不放行到默认流程）")
  void noBinding_returnsEmpty() {
    assertTrue(
        repository.findByEntityCode(ENTITY).isEmpty(),
        "未配绑定必须返回 empty——若返回某个默认 def_key，换流程改配置就失去意义");
  }

  @Test
  @DisplayName("已下线（enabled=0）的绑定不得被解析：配了不生效比没配更危险")
  void disabledBinding_isNotResolved() {
    givenBinding("B-2", TENANT, ENTITY, "expense", "submit", 0, 0);

    assertTrue(
        repository.findByEntityCode(ENTITY).isEmpty(), "enabled=0 必须过滤掉——否则运营下线流程后提交仍走进度已停的旧流程");
  }

  @Test
  @DisplayName("软删（deleted=1）的绑定不得被解析")
  void softDeletedBinding_isNotResolved() {
    givenBinding("B-3", TENANT, ENTITY, "expense", "submit", 1, 1);

    assertTrue(repository.findByEntityCode(ENTITY).isEmpty(), "deleted=1 必须过滤掉");
  }

  @Test
  @DisplayName("租户隔离：A 租户的绑定，B 租户查不到（否则提交会走到别家租户的流程上）")
  void bindingIsTenantScoped() {
    givenBinding("B-4", TENANT, ENTITY, "expense", "submit", 1, 0);
    try {
      TenantContext.setTenantId(OTHER_TENANT);

      assertTrue(
          repository.findByEntityCode(ENTITY).isEmpty(), "跨租户必须查不到——单租户测试完全看不出查询漏写 tenant_id 谓词");
    } finally {
      TenantContext.setTenantId(TENANT);
    }
  }

  @Test
  @DisplayName("负向：同一实体配了多条生效绑定必须当场炸错，不得静默挑一条")
  void duplicateActiveBindings_failFastRatherThanPickOne() {
    givenBinding("B-5a", TENANT, ENTITY, "expense", "submit", 1, 0);
    givenBinding("B-5b", TENANT, ENTITY, "expense_legacy", "submit", 1, 0);

    assertThrows(
        IncorrectResultSizeDataAccessException.class,
        () -> repository.findByEntityCode(ENTITY),
        "多条生效绑定必须 fail-fast——静默取第一条会让运营以为配了新流程、实际走的还是旧流程");
  }

  @Test
  @DisplayName("负向：缺租户上下文即失败关闭（不静默查全租户）")
  void missingTenantContext_failsClosed() {
    givenBinding("B-6", TENANT, ENTITY, "expense", "submit", 1, 0);
    TenantContext.clear();
    try {
      assertThrows(
          IllegalStateException.class,
          () -> repository.findByEntityCode(ENTITY),
          "无租户上下文必须拒绝查询——查全租户会让任意租户的提交解析到别家流程");
      assertNull(TenantContext.getTenantIdAsLong(), "失败后不得顺带设置租户上下文");
    } finally {
      TenantContext.setTenantId(TENANT);
    }
  }

  // ====================== 夹具 ======================

  /** 直落一行绑定（v1 只读，写路径随建模界面排期；种子复刻未来的运营配置写入）。 */
  private void givenBinding(
      String id,
      long tenant,
      String entityCode,
      String defKey,
      String startAction,
      int enabled,
      int deleted) {
    jdbc.update(
        "INSERT INTO wf_binding (id, tenant_id, entity_code, def_key, start_action, enabled,"
            + " created_by, created_at, updated_by, updated_at, deleted, version)"
            + " VALUES (?, ?, ?, ?, ?, ?, 'u1', NOW(), 'u1', NOW(), ?, 0)",
        id,
        tenant,
        entityCode,
        defKey,
        startAction,
        enabled,
        deleted);
  }
}
