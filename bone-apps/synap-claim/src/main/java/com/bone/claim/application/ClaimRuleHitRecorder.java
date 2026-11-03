package com.bone.claim.application;

import com.bone.claim.domain.model.rule.ClaimRuleHit;
import com.bone.claim.domain.model.rule.event.ClaimRulesEvaluatedEvent;
import com.bone.claim.domain.repository.ClaimRuleHitRepository;
import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.tenant.context.TenantContextRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 规则命中留痕记录器（§9.5 A3 / 推翻率·命中分析·人工确认率事实源）。
 *
 * <p>订阅进程内 {@link ClaimRulesEvaluatedEvent}（决策图在 {@code DecisionExecution} 上挂载、persist 时发布），
 * 在事务提交后按 {claimId, executionId, ruleCode} 维度 append 一笔 {@code t_claim_rule_hit}。写路径显式 {@link
 * TenantContextRunner#runAs} 声明租户（AFTER_COMMIT 后线程上下文已清空），同 {@code ClaimCaseTrackRecorder} / {@code
 * ClaimReviewRecordRecorder} / {@code ClaimStakeholderRecorder} 范式；零改动任何 emitter 与团队在途的决策图改造文件。
 *
 * <p><b>E-5.4 豁免</b>：本记录器仅 append 规则命中台账（合规中间态），领域事件已由决策执行聚合在原事务中发布， 此处不再派发领域事件。
 */
@NoDomainEvent
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimRuleHitRecorder {

  private final ClaimRuleHitRepository ruleHitRepository;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRulesEvaluated(ClaimRulesEvaluatedEvent event) {
    try {
      TenantContextRunner.runAs(
          event.tenantId(),
          () -> {
            for (ClaimRulesEvaluatedEvent.RuleHit h : event.hits()) {
              ruleHitRepository.save(
                  ClaimRuleHit.create(
                      event.tenantId(),
                      event.claimId(),
                      event.executionId(),
                      h.ruleCode(),
                      h.ruleVersion(),
                      h.ruleCategory(),
                      h.priority(),
                      h.inputSnapshot(),
                      h.conclusion(),
                      h.hitAt()));
            }
          });
    } catch (Exception e) {
      // 留痕落库失败不应回滚主流程；记录日志供人工 / 对账兜底。
      log.error(
          "规则命中留痕落库失败: claimId={}, executionId={}, tenantId={}",
          event.claimId(),
          event.executionId(),
          event.tenantId(),
          e);
    }
  }
}
