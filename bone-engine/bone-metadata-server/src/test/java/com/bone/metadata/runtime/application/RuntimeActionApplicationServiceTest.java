package com.bone.metadata.runtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.metadata.engine.runtime.JdbcRuntimeRecordService;
import com.bone.metadata.runtime.domain.CurrentRoleProvider;
import com.bone.metadata.runtime.domain.RuntimeActionContext;
import com.bone.metadata.runtime.domain.RuntimeActionDef;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 「退回指定节点」不推进平台状态的<b>行为</b>测试（{@link RuntimeActionApplicationService#invokeAction}）。
 *
 * <p>为什么不能只测 {@link RuntimeActionContext#keepsPlatformStatus}：那只证明「判据认得 KEEP」。真正决定单据死活的
 * 是应用服务是否<b>真的跳过</b> {@code updateState}——两者之间的接线错了（漏读判据、分支写反），
 * 判据单测照样全绿而线上仍是死单。故这里以协作者桩断言<b>调用发生/未发生</b>，对应实测缺陷形态：
 *
 * <pre>
 * 驳回退回 start → 200，实例 RUNNING node=mgr pending=1，但单据 status 被推到 REJECTED
 * → 再点「通过」恒 409 META_RUNTIME_ACTION_ILLEGAL_TRANSITION → 待办永远清不掉的死单
 * </pre>
 */
class RuntimeActionApplicationServiceTest {

  private static final long TENANT = 0L;
  private static final String ENTITY = "expense_reimbursement";
  private static final String RECORD = "9001";
  private static final String OPERATOR = "1";

  private JdbcRuntimeRecordService runtimeRecordService;
  private RuntimeActionDefRepository actionDefRepository;
  private RuntimeActionExecutionRepository executionRepository;
  private RuntimeActionDispatcher dispatcher;
  private RuntimeActionApplicationService service;

  @BeforeEach
  void setUp() {
    runtimeRecordService = mock(JdbcRuntimeRecordService.class);
    actionDefRepository = mock(RuntimeActionDefRepository.class);
    executionRepository = mock(RuntimeActionExecutionRepository.class);
    dispatcher = mock(RuntimeActionDispatcher.class);
    service =
        new RuntimeActionApplicationService(
            runtimeRecordService,
            actionDefRepository,
            executionRepository,
            dispatcher,
            new ObjectMapper(),
            mock(RuntimeAccessGuard.class),
            mock(UiSchemaRuleEnforcer.class),
            mock(CurrentRoleProvider.class));

    // 幂等键用 any() 而非 isNull()：单据动作 requires_idempotency=1，真实调用一定带键（null 只发生在非资金动作），
    // 用 isNull() 匹配不上会让 begin 落到默认返回值 0L，markSucceeded 断言随之失配。
    when(executionRepository.begin(
            anyLong(), anyString(), anyString(), anyString(), any(), anyString(), any()))
        .thenReturn(7L);
    // 必须显式桩 null：Mockito 对 Map 返回类型默认回空 Map（而非 null），会被幂等命中分支当作「已执行过」，
    // 进而在 (Number) hit.get("status") 上 NPE —— 与真实仓储「无记录返回 null」的契约不符。
    when(executionRepository.findByIdempotency(
            anyLong(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(null);
  }

  /** 驳回动作：SUBMITTED → REJECTED，逃逸（有工作流副作用）。 */
  private static RuntimeActionDef rejectDef() {
    return new RuntimeActionDef(
        1L,
        ENTITY,
        "reject",
        "驳回",
        "SUBMITTED",
        "REJECTED",
        "status",
        true,
        "handler",
        List.of(),
        null,
        true,
        0,
        false);
  }

  private static Map<String, Object> recordWithStatus(String status) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", RECORD);
    row.put("status", status);
    return row;
  }

  private void stubRejectFlow(Map<String, Object> sideEffect) {
    when(actionDefRepository.findByEntityAndCode(TENANT, ENTITY, "reject"))
        .thenReturn(Optional.of(rejectDef()));
    when(runtimeRecordService.getById(ENTITY, TENANT, RECORD))
        .thenReturn(recordWithStatus("SUBMITTED"));
    when(dispatcher.dispatch(any(RuntimeActionContext.class))).thenReturn(sideEffect);
  }

  @Test
  @DisplayName("处理器声明 KEEP → 绝不推进状态，toStatus 如实回「未变」")
  void keepStatus_skipsStateTransition() {
    Map<String, Object> sideEffect = new LinkedHashMap<>();
    sideEffect.put("backToNode", "start");
    sideEffect.put(
        RuntimeActionContext.OUTCOME_PLATFORM_STATUS, RuntimeActionContext.PLATFORM_STATUS_KEEP);
    stubRejectFlow(sideEffect);
    when(runtimeRecordService.getById(eq(ENTITY), eq(TENANT), eq(RECORD), isNull(), any()))
        .thenReturn(recordWithStatus("SUBMITTED"));

    Map<String, Object> result =
        service.invokeAction(ENTITY, TENANT, RECORD, "reject", Map.of(), OPERATOR, "k1", null);

    // 核心断言：状态推进分支根本没被调用 —— 否则单据落到 REJECTED 终态，待办永远清不掉。
    verify(runtimeRecordService, never())
        .updateState(
            anyString(),
            anyLong(),
            anyString(),
            anyString(),
            anyString(),
            any(),
            anyString(),
            anyString(),
            any(),
            any());
    assertThat(result.get("fromStatus")).isEqualTo("SUBMITTED");
    assertThat(result.get("toStatus")).isEqualTo("SUBMITTED");
    @SuppressWarnings("unchecked")
    Map<String, Object> returned = (Map<String, Object>) result.get("record");
    assertThat(returned.get("status")).isEqualTo("SUBMITTED");
    // 留痕不因「不推进」而丢：动作执行审计行照常关闭为成功。
    verify(executionRepository).markSucceeded(eq(7L), anyString());
  }

  @Test
  @DisplayName("处理器未声明 KEEP（整单驳回）→ 照常把状态推到 to_status")
  void plainReject_advancesState() {
    Map<String, Object> sideEffect = new LinkedHashMap<>();
    sideEffect.put("decision", "REJECT");
    stubRejectFlow(sideEffect);
    when(runtimeRecordService.updateState(
            eq(ENTITY),
            eq(TENANT),
            eq(RECORD),
            eq("status"),
            eq("REJECTED"),
            any(),
            eq(OPERATOR),
            eq("reject"),
            isNull(),
            any()))
        .thenReturn(recordWithStatus("REJECTED"));

    Map<String, Object> result =
        service.invokeAction(ENTITY, TENANT, RECORD, "reject", Map.of(), OPERATOR, "k2", null);

    verify(runtimeRecordService)
        .updateState(
            eq(ENTITY),
            eq(TENANT),
            eq(RECORD),
            eq("status"),
            eq("REJECTED"),
            any(),
            eq(OPERATOR),
            eq("reject"),
            isNull(),
            any());
    assertThat(result.get("toStatus")).isEqualTo("REJECTED");
    @SuppressWarnings("unchecked")
    Map<String, Object> returned = (Map<String, Object>) result.get("record");
    assertThat(returned.get("status")).isEqualTo("REJECTED");
  }
}
