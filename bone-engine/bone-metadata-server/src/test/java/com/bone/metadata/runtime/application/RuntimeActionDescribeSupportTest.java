package com.bone.metadata.runtime.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.metadata.runtime.domain.ActionParamSpec;
import com.bone.metadata.runtime.domain.RuntimeActionDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link RuntimeActionDescribeSupport} 单元测试——展示裁剪必须与执行门禁（{@link RuntimeAccessGuard#canInvoke}）同一判据。
 *
 * <p><b>为什么有这些用例</b>（2026-10-13 实测缺陷回归）：Describe 曾各自复制一份判定，漏掉「逃逸动作必须动作级细码」约束， 持有 {@code
 * runtime:*:write} 的用户看得到「确认打款」按钮、点击却 403。现判据收敛到 {@code RuntimeAccessGuard#canInvoke}，本测试锁死「按钮可见 ⇔
 * 可执行」。
 *
 * <p>每个用例自问「删掉实现哪一行会红」：
 *
 * <ul>
 *   <li>把 {@code canInvoke} 换回旧的自复制判定（通配写短路逃逸）→ 逃逸+通配写不可见用例红；
 *   <li>删掉 {@code defs.isEmpty()} 早退 → 空实体返 null 用例红；
 *   <li>删掉 from_status 归一（null→"*"）→ 通配键用例红。
 * </ul>
 */
class RuntimeActionDescribeSupportTest {

  private final RuntimeActionDescribeSupport support;

  RuntimeActionDescribeSupportTest() {
    // 真 Guard（判据真源）+ Fake 仓储：不 mock Spring 上下文，只对 SecurityContextHolder 设权限
    this.support =
        new RuntimeActionDescribeSupport(new FakeActionDefRepository(), new RuntimeAccessGuard());
  }

  @BeforeEach
  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private void authenticate(String... authorities) {
    var auth =
        new UsernamePasswordAuthenticationToken(
            "tester",
            null,
            java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @Test
  void escapeAction_wildcardWrite_isInvisible() {
    // 核心负向（漂移修复回归防线）：通配写持有者不得看到逃逸动作按钮
    authenticate("runtime:*:write");
    Map<String, Object> meta = support.describe(1001L, "expense_claim");
    assertNotNull(meta);
    @SuppressWarnings("unchecked")
    Map<String, List<Map<String, Object>>> grouped =
        (Map<String, List<Map<String, Object>>>) meta.get("allowedActionsByStatus");
    List<Map<String, Object>> approved = grouped.getOrDefault("APPROVED", List.of());
    assertTrue(
        approved.stream().noneMatch(v -> "confirmPayout".equals(v.get("code"))),
        "逃逸动作对仅持通配写的主体必须不可见（可见=诱导越权探测，与 §4.3.1 相悖）");
    assertTrue(
        approved.isEmpty() || approved.stream().allMatch(v -> !"escape".equals(v.get("kind"))),
        "APPROVED 状态组不得残留任何逃逸动作");
  }

  @Test
  void escapeAction_fineCode_isVisible() {
    authenticate("runtime:expense_claim:confirmPayout", "runtime:expense_claim:read");
    Map<String, Object> meta = support.describe(1001L, "expense_claim");
    assertNotNull(meta);
    @SuppressWarnings("unchecked")
    Map<String, List<Map<String, Object>>> grouped =
        (Map<String, List<Map<String, Object>>>) meta.get("allowedActionsByStatus");
    List<Map<String, Object>> approved = grouped.get("APPROVED");
    assertNotNull(approved, "细码持有者应看到逃逸动作分组");
    assertTrue(approved.stream().anyMatch(v -> "confirmPayout".equals(v.get("code"))));
    Map<String, Object> view = approved.get(0);
    assertEquals("escape", view.get("kind"), "视图须携带 kind 供前端渲染确认框");
    assertTrue(Boolean.TRUE.equals(view.get("confirm")), "逃逸动作必须二次确认");
  }

  @Test
  void nonEscapeAction_entityWrite_isVisible() {
    authenticate("runtime:expense_claim:write");
    Map<String, Object> meta = support.describe(1001L, "expense_claim");
    assertNotNull(meta);
    @SuppressWarnings("unchecked")
    Map<String, List<Map<String, Object>>> grouped =
        (Map<String, List<Map<String, Object>>>) meta.get("allowedActionsByStatus");
    List<Map<String, Object>> draft = grouped.get("DRAFT");
    assertNotNull(draft);
    assertTrue(draft.stream().anyMatch(v -> "submit".equals(v.get("code"))));
    assertTrue(draft.stream().noneMatch(v -> "confirmPayout".equals(v.get("code"))));
  }

  @Test
  void noAuthority_actionsInvisible() {
    authenticate();
    Map<String, Object> meta = support.describe(1001L, "expense_claim");
    assertNotNull(meta);
    @SuppressWarnings("unchecked")
    Map<String, List<Map<String, Object>>> grouped =
        (Map<String, List<Map<String, Object>>>) meta.get("allowedActionsByStatus");
    assertTrue(grouped.isEmpty(), "无任何权限码时不得泄露任何动作（含状态流转）");
  }

  @Test
  void platformAdmin_seesAll() {
    authenticate("platform.admin");
    Map<String, Object> meta = support.describe(1001L, "expense_claim");
    assertNotNull(meta);
    @SuppressWarnings("unchecked")
    Map<String, List<Map<String, Object>>> grouped =
        (Map<String, List<Map<String, Object>>>) meta.get("allowedActionsByStatus");
    assertTrue(
        grouped.get("APPROVED").stream().anyMatch(v -> "confirmPayout".equals(v.get("code"))));
  }

  @Test
  void entityWithoutActions_returnsNull() {
    Map<String, Object> meta = support.describe(1001L, "plain_entity");
    assertNull(meta, "无动作定义的实体返 null（PageResult.meta 不序列化，避免无意义空对象）");
  }

  @Test
  void nullFromStatus_groupedUnderWildcardKey() {
    authenticate("runtime:any_status_entity:write");
    Map<String, Object> meta = support.describe(1001L, "any_status_entity");
    assertNotNull(meta);
    @SuppressWarnings("unchecked")
    Map<String, List<Map<String, Object>>> grouped =
        (Map<String, List<Map<String, Object>>>) meta.get("allowedActionsByStatus");
    assertTrue(grouped.containsKey("*"), "from_status 为空归入通配键 \"*\"（前端兜底查不到时命中）");
  }

  /** 手写假仓储：固定三实体动作集，不引 Mockito。 */
  private static final class FakeActionDefRepository implements RuntimeActionDefRepository {

    @Override
    public Optional<RuntimeActionDef> findByEntityAndCode(
        long tenantId, String entityCode, String actionCode) {
      return defsOf(entityCode).stream()
          .filter(d -> d.getActionCode().equals(actionCode))
          .findFirst();
    }

    @Override
    public List<RuntimeActionDef> listByEntity(long tenantId, String entityCode) {
      return defsOf(entityCode);
    }

    @Override
    public List<RuntimeActionDef> listByEntityAndFromStatus(
        long tenantId, String entityCode, String fromStatus) {
      return defsOf(entityCode).stream().filter(d -> fromStatus.equals(d.getFromStatus())).toList();
    }

    private static List<RuntimeActionDef> defsOf(String entityCode) {
      if ("plain_entity".equals(entityCode)) {
        return List.of();
      }
      if ("any_status_entity".equals(entityCode)) {
        return List.of(def(1L, "any_status_entity", "bulkUpdate", "批量更新", null, null, false));
      }
      List<RuntimeActionDef> defs = new ArrayList<>();
      defs.add(def(2L, entityCode, "submit", "提交", "DRAFT", "SUBMITTED", false));
      defs.add(def(3L, entityCode, "confirmPayout", "确认打款", "APPROVED", "PAID", true));
      return defs;
    }

    private static RuntimeActionDef def(
        long id,
        String entityCode,
        String actionCode,
        String label,
        String from,
        String to,
        boolean escape) {
      return new RuntimeActionDef(
          id,
          entityCode,
          actionCode,
          label,
          from,
          to,
          "status",
          escape,
          null,
          List.<ActionParamSpec>of(),
          "runtime:" + entityCode + ":" + actionCode,
          escape,
          10);
    }
  }
}
