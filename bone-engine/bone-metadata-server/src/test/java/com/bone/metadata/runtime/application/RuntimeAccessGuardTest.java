package com.bone.metadata.runtime.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.runtime.domain.ActionParamSpec;
import com.bone.metadata.runtime.domain.RuntimeActionDef;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link RuntimeAccessGuard} 单元测试——布尔判据（canXxx）与抛掷判据（requireXxx）一致性 + 实体级授权核心语义。
 *
 * <p>每个用例自问「删掉实现哪一行会红」：
 *
 * <ul>
 *   <li>删掉 canRead 的 {@code runtime:{entity}:read} 分支 → 实体级读用例红；
 *   <li>删掉 canWrite 的 {@code runtime:*:write} 通配分支 → 通配写用例红；
 *   <li>删掉 canInvoke 的逃逸细码门槛（退回「实体写/通配写短路」）→ 逃逸+通配写不可见用例红（2026-10-13 Describe 漂移修复的回归防线）；
 *   <li>删掉 requireRead 的 {@code !canRead} 取反 → 无码抛掷用例红。
 * </ul>
 *
 * <p><b>为什么断言「粗码不放行」</b>：{@code metadata:runtime:read|write} 是租户内全库 CRUD 的旧形状码，RuntimeAccessGuard
 * javadoc 明确「旧码一旦保留为回退分支，实体级授权形同虚设」。注解层（P1-2）已同步改为 canRead/canWrite 动态判据，本测试锁死「仅持粗码 = 无实体级权限」。
 */
class RuntimeAccessGuardTest {

  private final RuntimeAccessGuard guard = new RuntimeAccessGuard();

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

  // ── canRead / requireRead ──────────────────────────────────────────────

  @Test
  void entityScopedReadCode_passes() {
    authenticate("runtime:expense_reimbursement:read");
    assertTrue(guard.canRead("expense_reimbursement"), "实体级读码必须放行读");
    guard.requireRead("expense_reimbursement"); // 不抛即通过
  }

  @Test
  void wildcardReadCode_passes() {
    authenticate("runtime:*:read");
    assertTrue(guard.canRead("expense_reimbursement"), "通配读码对任意实体放行");
    assertTrue(guard.canRead("any_other_entity"), "通配语义与实体编码无关");
  }

  @Test
  void platformAdmin_passes() {
    authenticate("platform.admin");
    assertTrue(guard.canRead("expense_reimbursement"), "平台管理员是数据修复通道（§4.5）");
    assertTrue(guard.canWrite("expense_reimbursement"));
    assertTrue(guard.isPlatformAdmin());
  }

  @Test
  void coarseOnlyCode_denied() {
    // 核心负向：旧粗码 metadata:runtime:read 单独持有 → 实体级判据不认
    authenticate("metadata:runtime:read");
    assertFalse(guard.canRead("expense_reimbursement"), "旧粗码不得回退放行（否则实体级授权形同虚设）");
    RuntimeRecordException ex =
        assertThrows(
            RuntimeRecordException.class, () -> guard.requireRead("expense_reimbursement"));
    assertEquals(MetadataErrorCodes.RUNTIME_ENTITY_FORBIDDEN, ex.getErrorCode());
  }

  @Test
  void noAuthorities_denied() {
    authenticate(); // 空权限集
    assertFalse(guard.canRead("expense_reimbursement"));
    RuntimeRecordException ex =
        assertThrows(
            RuntimeRecordException.class, () -> guard.requireRead("expense_reimbursement"));
    assertEquals(MetadataErrorCodes.RUNTIME_ENTITY_FORBIDDEN, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("runtime:expense_reimbursement:read"), "拒绝消息须点名缺的码");
  }

  @Test
  void otherEntityCode_doesNotLeak() {
    authenticate("runtime:other_entity:read");
    assertFalse(guard.canRead("expense_reimbursement"), "A 实体的读码不得放行 B 实体");
    assertTrue(guard.canRead("other_entity"));
  }

  // ── canWrite / requireWrite ────────────────────────────────────────────

  @Test
  void entityScopedWriteCode_passes() {
    authenticate("runtime:expense_reimbursement:write");
    assertTrue(guard.canWrite("expense_reimbursement"));
    guard.requireWrite("expense_reimbursement");
  }

  @Test
  void coarseWriteOnlyCode_denied() {
    authenticate("metadata:runtime:write");
    assertFalse(guard.canWrite("expense_reimbursement"), "旧粗写码不得回退放行");
  }

  @Test
  void readCodeDoesNotGrantWrite() {
    authenticate("runtime:expense_reimbursement:read");
    assertFalse(guard.canWrite("expense_reimbursement"), "读码不隐含写码");
    assertThrows(RuntimeRecordException.class, () -> guard.requireWrite("expense_reimbursement"));
  }

  // ── canInvoke / requireAction（逃逸动作必须细码，§4.6） ─────────────────

  @Test
  void escapeAction_wildcardWrite_stillDenied() {
    // 2026-10-13 Describe 漂移修复的核心断言：通配写 ≠ 放款授权
    authenticate("runtime:*:write");
    RuntimeActionDef escape = escapeDef();
    assertFalse(guard.canInvoke(escape), "逃逸动作：实体级写/通配写不得短路细码判定");
    RuntimeRecordException ex =
        assertThrows(RuntimeRecordException.class, () -> guard.requireAction(escape));
    assertEquals(MetadataErrorCodes.RUNTIME_FORBIDDEN_ACTION, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("confirmPayout"), "逃逸动作拒绝消息须点名动作码");
  }

  @Test
  void escapeAction_entityWrite_stillDenied() {
    authenticate("runtime:expense_reimbursement:write");
    assertFalse(guard.canInvoke(escapeDef()), "逃逸动作：实体级写也不覆盖资金授权面");
  }

  @Test
  void escapeAction_fineCode_passes() {
    authenticate("runtime:expense_reimbursement:confirmPayout");
    RuntimeActionDef escape = escapeDef();
    assertTrue(guard.canInvoke(escape), "逃逸动作：动作级细码放行");
    guard.requireAction(escape);
  }

  @Test
  void escapeAction_declaredCode_passes() {
    // def.permissionCode（声明码）与 runtime:{entity}:{action} 同权——种子可用任一形状登记
    authenticate("metadata:expense:payout"); // 任意声明码形状，只要与 def.permissionCode 一致
    RuntimeActionDef escape = escapeDef("metadata:expense:payout");
    assertTrue(guard.canInvoke(escape), "声明码命中即放行（0034 种子按声明码登记）");
  }

  @Test
  void escapeAction_platformAdmin_passes() {
    authenticate("platform.admin");
    assertTrue(guard.canInvoke(escapeDef()), "平台管理员 early-return（强制审计由执行链保证）");
  }

  @Test
  void nonEscapeAction_entityWrite_passes() {
    authenticate("runtime:expense_reimbursement:write");
    RuntimeActionDef submit = nonEscapeDef();
    assertTrue(guard.canInvoke(submit), "非逃逸动作：实体级写即授权（状态流转与 CRUD 共用授权面）");
  }

  @Test
  void nonEscapeAction_wildcardWrite_passes() {
    authenticate("runtime:*:write");
    assertTrue(guard.canInvoke(nonEscapeDef()), "非逃逸动作：通配写即授权");
  }

  @Test
  void nonEscapeAction_fineCode_passes() {
    authenticate("runtime:expense_reimbursement:submit");
    assertTrue(guard.canInvoke(nonEscapeDef()), "非逃逸动作：细码即授权");
  }

  @Test
  void nonEscapeAction_noAuthority_denied() {
    authenticate();
    RuntimeRecordException ex =
        assertThrows(RuntimeRecordException.class, () -> guard.requireAction(nonEscapeDef()));
    assertEquals(MetadataErrorCodes.RUNTIME_FORBIDDEN_ACTION, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("runtime:expense_reimbursement:submit"));
  }

  @Test
  void nonEscapeAction_declaredCodeOnly_passes() {
    authenticate("metadata:expense:submit");
    assertTrue(guard.canInvoke(nonEscapeDef("metadata:expense:submit")));
  }

  // ── can/require 一致性（同一判据两个出口，禁止漂移） ─────────────────────

  @Test
  void booleanAndThrowingAreConsistent() {
    for (String codes :
        List.of("", "runtime:expense_reimbursement:read", "runtime:*:write", "platform.admin")) {
      for (String entity : Set.of("expense_reimbursement", "other")) {
        authenticate(codes.isEmpty() ? new String[0] : new String[] {codes});
        boolean can = guard.canRead(entity);
        if (can) {
          guard.requireRead(entity); // 不抛
        } else {
          assertThrows(RuntimeRecordException.class, () -> guard.requireRead(entity));
        }
      }
    }
  }

  // ── 测试夹具 ───────────────────────────────────────────────────────────

  private static RuntimeActionDef escapeDef() {
    return escapeDef("runtime:expense_reimbursement:confirmPayout");
  }

  private static RuntimeActionDef escapeDef(String permissionCode) {
    return new RuntimeActionDef(
        1L,
        "expense_reimbursement",
        "confirmPayout",
        "确认打款",
        "APPROVED",
        "PAID",
        "status",
        true, // escape：资金副作用
        null,
        List.<ActionParamSpec>of(),
        permissionCode,
        true,
        30,
        false);
  }

  private static RuntimeActionDef nonEscapeDef() {
    return nonEscapeDef("runtime:expense_reimbursement:submit");
  }

  private static RuntimeActionDef nonEscapeDef(String permissionCode) {
    return new RuntimeActionDef(
        2L,
        "expense_reimbursement",
        "submit",
        "提交",
        "DRAFT",
        "SUBMITTED",
        "status",
        false, // 非逃逸：状态流转
        null,
        List.<ActionParamSpec>of(),
        permissionCode,
        false,
        10,
        false);
  }
}
