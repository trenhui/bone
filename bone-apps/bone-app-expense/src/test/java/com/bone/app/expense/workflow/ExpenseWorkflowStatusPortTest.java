package com.bone.app.expense.workflow;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.ports.spi.WorkflowIdGenerator;
import com.bone.workflow.starter.outbox.WorkflowStatusOutboxRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ExpenseWorkflowStatusPort}：全部意图只记录不调用动作（action_code=null）——人工动作同链路已落地 status， 重复投影会撞
 * from_status 校验产生 FAILED 死信。
 */
class ExpenseWorkflowStatusPortTest {

  private final WorkflowStatusOutboxRepository outbox = mock(WorkflowStatusOutboxRepository.class);
  private final WorkflowIdGenerator idGenerator = mock(WorkflowIdGenerator.class);
  private final ExpenseWorkflowStatusPort port =
      new ExpenseWorkflowStatusPort(
          WorkflowFixtures.outboxProvider(outbox),
          WorkflowFixtures.idGeneratorProvider(idGenerator));

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(7L);
    when(idGenerator.nextId()).thenReturn("intent-1");
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("markApproved 记录 action_code=null 的意图（不重复调用 approve 动作）")
  void markApproved_recordOnly() {
    port.markApproved("expense_reimbursement", "9", "wf-1", "u-manager");

    verify(outbox)
        .insert(
            argThat(
                i ->
                    i.intent().equals("APPROVED")
                        && i.actionCode() == null
                        && i.instanceId().equals("wf-1")
                        && i.tenantId().equals(7L)
                        && i.state().equals("NEW")));
  }

  @Test
  @DisplayName("markRejected 携带驳回原因留痕")
  void markRejected_carriesReason() {
    port.markRejected("expense_reimbursement", "9", "wf-1", "u-manager", "缺发票");

    verify(outbox)
        .insert(
            argThat(
                i ->
                    i.intent().equals("REJECTED")
                        && "缺发票".equals(i.paramsJson())
                        && i.actionCode() == null));
  }

  @Test
  @DisplayName("markWithdrawn / markResumed 同样只留痕")
  void withdrawnAndResumed_recordOnly() {
    port.markWithdrawn("expense_reimbursement", "9", "wf-1", "u-applicant");
    port.markResumed("expense_reimbursement", "9", "wf-1");

    verify(outbox).insert(argThat(i -> i.intent().equals("WITHDRAWN") && i.actionCode() == null));
    verify(outbox).insert(argThat(i -> i.intent().equals("RESUMED") && i.actionCode() == null));
  }

  @Test
  @DisplayName("markSuspended / markTerminated 只留痕不抛异常（告警走日志，不阻断审批主链路）")
  void suspendedAndTerminated_recordOnly() {
    port.markSuspended("expense_reimbursement", "9", "wf-1", "审批人解析不出");
    port.markTerminated("expense_reimbursement", "9", "wf-1", "admin", "错提单据");

    verify(outbox).insert(argThat(i -> i.intent().equals("SUSPENDED") && i.actionCode() == null));
    verify(outbox).insert(argThat(i -> i.intent().equals("TERMINATED") && i.actionCode() == null));
  }

  @Test
  @DisplayName("意图 id 由平台 id 生成器分配，租户取自当前上下文（行内携带供中继重建）")
  void intentIdAndTenant_fromPlatform() {
    port.markApproved("expense_reimbursement", "9", "wf-1", "u-manager");

    verify(idGenerator).nextId();
    verify(outbox).insert(argThat(i -> "intent-1".equals(i.id()) && i.tenantId().equals(7L)));
  }

  @Test
  @DisplayName("markApproved 不调用任何平台动作（无 RuntimeActionApplicationService 依赖，结构保证）")
  void noActionInvocation_byConstruction() {
    // 结构保证：本端口只依赖 outbox 仓储与 id 生成器，没有任何动作服务依赖——重复投影在编译期即不可能。
    port.markApproved("expense_reimbursement", "9", "wf-1", "u-manager");
    verify(outbox).insert(argThat(i -> i.actionCode() == null));
  }

  @Test
  @DisplayName("发件箱/id 生成器缺失 → 留痕降级为日志，不抛异常（不打挂宿主）")
  void missingInfrastructure_degradesToLog() {
    ExpenseWorkflowStatusPort degraded =
        new ExpenseWorkflowStatusPort(
            WorkflowFixtures.emptyProvider(WorkflowStatusOutboxRepository.class),
            WorkflowFixtures.emptyProvider(WorkflowIdGenerator.class));

    assertThatNoException()
        .isThrownBy(() -> degraded.markApproved("expense_reimbursement", "9", "wf-1", "u-manager"));
    verifyNoInteractions(outbox, idGenerator);
  }
}
