package com.bone.metadata.runtime.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.runtime.RuntimeEngineConfiguration;
import com.bone.metadata.runtime.domain.ActionParamSpec;
import com.bone.metadata.runtime.domain.RuntimeActionDef;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * {@link RuntimeStatusGuard} 单元测试（负向探针纪律、v2 契约）。
 *
 * <p>v2 契约（与 v1 的差异）：守卫不做「静默剥离 + 注入」——那个语义会让调用方误以为自己提交的 {@code status=PAID} 已生效（Runtime §4.11 禁止项
 * 6）。guard 职责收缩为<strong>「携带即 422」快速失败</strong>； 入口状态注入下沉到 engine 写路径唯一出口（见 engine 模块 {@code
 * JdbcRuntimeRecordServiceInjectEntryStateTest} 与 {@link #resolverSwapsArgumentOrder()} 的接线测试）。
 *
 * <p>每个用例自问「删掉实现哪一行会红」：
 *
 * <ul>
 *   <li>删掉 {@code applyCreateGuard} 的 {@code rejectIfPresent} → bypass 用例红；
 *   <li>删掉「无状态机直接 return」→ 无状态机实体被误伤用例红；
 *   <li>删掉 statusFieldOf 的缺省回退 → 自定义状态字段用例红；
 *   <li>resolver 参数序写反（(tenantId, entityCode) 直传）→ 接线用例红；
 *   <li>删掉 {@code entryStatusOf} 的 is_entry 分支 → 全环显式入口用例红（0041：巡检机器退回 fail-closed，创建死锁）。
 * </ul>
 */
class RuntimeStatusGuardTest {

  /** 手写假仓储（不引 Mockito，保持测试零额外依赖）。 */
  private static final class FakeActionDefRepository implements RuntimeActionDefRepository {
    private final List<RuntimeActionDef> defs;

    FakeActionDefRepository(List<RuntimeActionDef> defs) {
      this.defs = defs;
    }

    @Override
    public Optional<RuntimeActionDef> findByEntityAndCode(
        long tenantId, String entityCode, String actionCode) {
      return defs.stream().filter(d -> d.getActionCode().equals(actionCode)).findFirst();
    }

    @Override
    public List<RuntimeActionDef> listByEntity(long tenantId, String entityCode) {
      return defs;
    }

    @Override
    public List<RuntimeActionDef> listByEntityAndFromStatus(
        long tenantId, String entityCode, String fromStatus) {
      return defs.stream().filter(d -> fromStatus.equals(d.getFromStatus())).toList();
    }
  }

  private static RuntimeActionDef def(
      String code, String from, String to, int sortOrder, String statusField) {
    return def(code, from, to, sortOrder, statusField, false);
  }

  private static RuntimeActionDef def(
      String code, String from, String to, int sortOrder, String statusField, boolean entry) {
    return new RuntimeActionDef(
        1L,
        "expense_claim",
        code,
        code,
        from,
        to,
        statusField,
        false,
        null,
        List.<ActionParamSpec>of(),
        "metadata:runtime:write",
        false,
        sortOrder,
        entry);
  }

  /** expense_claim 形态：DRAFT→SUBMITTED→APPROVED→PAID（+ SUBMITTED→REJECTED）。 */
  private static final List<RuntimeActionDef> CLAIM_DEFS =
      List.of(
          def("submit", "DRAFT", "SUBMITTED", 10, "status"),
          def("approve", "SUBMITTED", "APPROVED", 20, "status"),
          def("reject", "SUBMITTED", "REJECTED", 21, "status"),
          def("confirmPayout", "APPROVED", "PAID", 30, "status"));

  @Test
  void create_withStatus_isRejectedWith422() {
    RuntimeStatusGuard guard = new RuntimeStatusGuard(new FakeActionDefRepository(CLAIM_DEFS));
    Map<String, Object> body = new HashMap<>();
    body.put("title", "x");
    body.put("status", "PAID");
    RuntimeRecordException ex =
        assertThrows(
            RuntimeRecordException.class, () -> guard.applyCreateGuard(0L, "expense_claim", body));
    assertEquals(
        MetadataErrorCodes.RUNTIME_FIELD_PROTECTED,
        ex.getErrorCode(),
        "create 携带 status 必须 422 快速失败，绝不静默剥离（防调用方误以为已生效）");
    assertEquals("PAID", body.get("status"), "422 用例：body 不做改写（拒绝优先于变异）");
  }

  @Test
  void update_withStatus_isRejectedWith422() {
    RuntimeStatusGuard guard = new RuntimeStatusGuard(new FakeActionDefRepository(CLAIM_DEFS));
    Map<String, Object> body = new HashMap<>();
    body.put("title", "y");
    body.put("status", "PAID");
    RuntimeRecordException ex =
        assertThrows(
            RuntimeRecordException.class, () -> guard.applyUpdateGuard(0L, "expense_claim", body));
    assertEquals(
        MetadataErrorCodes.RUNTIME_FIELD_PROTECTED,
        ex.getErrorCode(),
        "update 携带 status 必须 422，状态只允许经动作端点流转");
    assertEquals("y", body.get("title"), "业务字段本身合法，报错信息不得误伤");
  }

  @Test
  void create_withoutStatus_passesThrough() {
    // 合法路径（创建不带状态，入口态由 engine 写路径注入）：守卫必须放行
    RuntimeStatusGuard guard = new RuntimeStatusGuard(new FakeActionDefRepository(CLAIM_DEFS));
    Map<String, Object> body = new HashMap<>();
    body.put("title", "x");
    guard.applyCreateGuard(0L, "expense_claim", body);
    assertFalse(body.containsKey("status"), "合法创建不携带 status，守卫放行且不注入（注入属 engine 写路径职责）");
  }

  @Test
  void entityWithoutStateMachine_bodyUntouched() {
    RuntimeStatusGuard guard = new RuntimeStatusGuard(new FakeActionDefRepository(List.of()));
    Map<String, Object> createBody = new HashMap<>();
    createBody.put("status", "WHATEVER");
    guard.applyCreateGuard(0L, "plain_entity", createBody);
    assertEquals("WHATEVER", createBody.get("status"), "无状态机实体的 create 不得被守卫改写");

    Map<String, Object> updateBody = new HashMap<>();
    updateBody.put("status", "WHATEVER");
    guard.applyUpdateGuard(0L, "plain_entity", updateBody);
    assertEquals("WHATEVER", updateBody.get("status"), "无状态机实体的 update 不得被守卫改写");
  }

  @Test
  void customStatusField_isRejectedWith422() {
    List<RuntimeActionDef> custom = List.of(def("go", "NEW", "DONE", 10, "order_stage"));
    RuntimeStatusGuard guard = new RuntimeStatusGuard(new FakeActionDefRepository(custom));
    Map<String, Object> body = new HashMap<>();
    body.put("order_stage", "DONE");
    body.put("status", "BUSINESS_FIELD");
    RuntimeRecordException ex =
        assertThrows(RuntimeRecordException.class, () -> guard.applyCreateGuard(0L, "order", body));
    assertEquals(
        MetadataErrorCodes.RUNTIME_FIELD_PROTECTED,
        ex.getErrorCode(),
        "自定义 status_field（order_stage）同样受独占保护");
    assertFalse(
        body.containsKey("status") && "SHOULD_NOT_TOUCH".equals(body.get("status")),
        "同名的普通业务字段 status 不参与保护判定（保护的是 order_stage）");
  }

  @Test
  void entryStatus_derivedFromSetMinusToSet() {
    RuntimeStatusGuard guard = new RuntimeStatusGuard(new FakeActionDefRepository(CLAIM_DEFS));
    // from={DRAFT,SUBMITTED,APPROVED}, to={SUBMITTED,APPROVED,REJECTED,PAID} ⇒ 入口=DRAFT
    assertEquals("DRAFT", guard.entryStatus(0L, "expense_claim"));
  }

  @Test
  void entryStatus_explicitEntryAction_resolvesFullCycle() {
    // 0041 核心场景：全环（巡检 OPEN↔CLOSED）图推导无解 ⇒ 必须显式标记入口动作，其 from_status 即初始状态。
    // 负向探针：删掉 entryStatusOf 的 is_entry 分支 → 本用例红（回退全环 fail-closed）。
    List<RuntimeActionDef> inspection =
        List.of(
            def("close", "OPEN", "CLOSED", 10, "status", true),
            def("reopen", "CLOSED", "OPEN", 20, "status"));
    RuntimeStatusGuard guard = new RuntimeStatusGuard(new FakeActionDefRepository(inspection));
    assertEquals("OPEN", guard.entryStatus(0L, "insp"), "显式入口动作的源状态即初始状态（全环机器的唯一活路）");
  }

  @Test
  void entryStatus_explicitEntry_beatsGraphDerivation() {
    // 显式优先于推断：建模者声明即意图。标记 approve 为入口 ⇒ SUBMITTED，而非图推导的 DRAFT。
    List<RuntimeActionDef> marked =
        List.of(
            def("submit", "DRAFT", "SUBMITTED", 10, "status"),
            def("approve", "SUBMITTED", "APPROVED", 20, "status", true));
    RuntimeStatusGuard guard = new RuntimeStatusGuard(new FakeActionDefRepository(marked));
    assertEquals("SUBMITTED", guard.entryStatus(0L, "expense_claim"), "显式入口动作覆盖图推导结果");
  }

  @Test
  void entryStatus_entryActionWithBlankFromStatus_ignored() {
    // 入口动作缺 from_status 是建模错误：不得当成入口（否则初始状态为空白），回退图推导。
    List<RuntimeActionDef> badEntry =
        List.of(
            def("weird", null, "OPEN", 5, "status", true),
            def("close", "OPEN", "CLOSED", 10, "status"),
            def("reopen", "CLOSED", "OPEN", 20, "status"));
    RuntimeStatusGuard guard = new RuntimeStatusGuard(new FakeActionDefRepository(badEntry));
    assertNull(guard.entryStatus(0L, "insp"), "空白源态的入口标记被忽略；全环仍 fail-closed");
  }

  @Test
  void entryStatus_noStateMachine_returnsNull() {
    RuntimeStatusGuard guard = new RuntimeStatusGuard(new FakeActionDefRepository(List.of()));
    assertNull(guard.entryStatus(0L, "plain_entity"), "无状态机实体没有入口状态");
  }

  @Test
  void entryStatus_fullCycle_failClosed_null() {
    // 全环：A→B, B→A ⇒ 无入口，fail-closed（返回 null，由必填校验拒绝，绝不采信客户端值）
    List<RuntimeActionDef> cycle =
        List.of(def("a2b", "A", "B", 10, "status"), def("b2a", "B", "A", 20, "status"));
    RuntimeStatusGuard guard = new RuntimeStatusGuard(new FakeActionDefRepository(cycle));
    assertNull(guard.entryStatus(0L, "cycle_entity"), "入口不可推导必须 fail-closed");
  }

  @Test
  void entryStatus_nullFromStatus_isNotTreatedAsEntry() {
    // from=null 表示「任意状态可触发」的边，不得被当作入口候选
    List<RuntimeActionDef> anyStateEdge = List.of(def("cancel", null, "CANCELLED", 10, "status"));
    RuntimeStatusGuard guard = new RuntimeStatusGuard(new FakeActionDefRepository(anyStateEdge));
    assertNull(guard.entryStatus(0L, "x"), "仅有无入边约束的任意态边时入口不可推导");
  }

  @Test
  void entryStatus_isDeterministic_onRepeatedInvocation() {
    RuntimeStatusGuard guard = new RuntimeStatusGuard(new FakeActionDefRepository(CLAIM_DEFS));
    assertEquals(guard.entryStatus(0L, "expense_claim"), guard.entryStatus(0L, "expense_claim"));
  }

  /** 接线契约：adapter 把 resolver 的 (entityCode, tenantId) 换序后委托 guard，防参数序写反。 */
  @Test
  void resolverSwapsArgumentOrder() {
    RuntimeStatusGuard guard = new RuntimeStatusGuard(new FakeActionDefRepository(CLAIM_DEFS));
    var resolver = new RuntimeEngineConfiguration().runtimeStateEntryResolver(guard);
    assertEquals(
        "DRAFT",
        resolver.entryState("expense_claim", 77L),
        "resolver(entityCode, tenantId) → guard.entryStatus(tenantId, entityCode)：租户/实体不可换错位");
    assertTrue(
        resolver.entryState("unknown_entity", 77L) == null
            || "DRAFT".equals(resolver.entryState("unknown_entity", 77L)),
        "Fake 仓储不分实体；此断言仅约束不抛异常");
  }
}
