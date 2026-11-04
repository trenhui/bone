package com.bone.claim.application;

import com.bone.claim.domain.model.evidence.event.EvidenceSetLockedEvent;
import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.tenant.context.TenantContextRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 链路联动：证据集锁定后自动触发一次决策图执行。
 *
 * <p>此前「报案 → 初审 → 采集 → 审核 → 结案」各环节的能力都在，但决策图只能由两个手工 HTTP 端点触发，
 * 链路无人串联、自动化率无法测量。本记录器补上「材料齐备并锁定」这一唯一业务时点到决策执行的边。
 *
 * <p><b>默认关闭</b>（{@code bone.claim.auto-decision.enabled=false}）：自动决策会消费 LLM 配额并可能 触发自动放款，须在 G3
 * 放量演练（SLO / 错误预算 / 自动回滚 / kill switch）通过后才按租户打开； 开关只控制「自动触发」，不改变决策图内部的资金红线与人工终审规则。
 *
 * <p>AFTER_COMMIT + 显式租户：不在锁定事务内跑 LLM，也不让异步线程丢失租户上下文。 触发失败只记结构化告警，绝不回滚已提交的证据锁定。
 */
@NoDomainEvent
@Slf4j
@Component
@RequiredArgsConstructor
public class DecisionTriggerRecorder {

  private final DecisionRunApplicationService decisionRunApplicationService;

  @Value("${bone.claim.auto-decision.enabled:false}")
  private boolean enabled;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onEvidenceSetLocked(EvidenceSetLockedEvent event) {
    if (!enabled) {
      return;
    }
    try {
      TenantContextRunner.runAs(
          event.tenantId(),
          () ->
              decisionRunApplicationService.execute(
                  event.claimId(),
                  // 版本由执行器从 AI 节点自报值回填；此处不猜模型/提示词版本。
                  new DecisionGraphExecutor.DecisionVersions("", "", "", "", "")));
      log.info(
          "[DECISION_TRIGGERED] claimId={} evidenceCount={}",
          event.claimId(),
          event.evidenceCount());
    } catch (Exception e) {
      log.error(
          "[DECISION_TRIGGER_FAILED] claimId={} tenantId={}", event.claimId(), event.tenantId(), e);
    }
  }
}
