package com.bone.claim.application;

import com.bone.claim.domain.model.claim.event.ClaimDecisionMadeEvent;
import com.bone.claim.domain.model.review.ClaimReviewRecord;
import com.bone.claim.domain.repository.ClaimReviewRecordRepository;
import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.tenant.context.TenantContextRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 核赔决策留痕记录器（§9.5 A1 / §11.7 不变量 5 / §11.10）。
 *
 * <p>订阅进程内 {@link ClaimDecisionMadeEvent}（人工核赔决策生效必发，由 {@code ClaimCase.makeDecision}
 * 在原事务发布），在事务提交后 append 一笔 {@code t_claim_review_record}。写路径显式 {@link TenantContextRunner#runAs}
 * 声明租户（AFTER_COMMIT 后线程上下文已清空，写路径须显式租户，否则漏落； 同 {@code ClaimCaseTrackRecorder}）。
 *
 * <p>这是「大额双人复核 / 权限校验」的不可篡改事实来源：谁在何时以什么依据批准/驳回了一笔赔付。 字段来源：claimId / tenantId / reviewerId /
 * decision / comment 直接取自事件；operatorId（发起人）、 amount（核定金额）、settlement_no（结算单号）事件未携带，按空 /
 * 零默认——若后续需完整对账，应在 {@code ClaimDecisionMadeEvent} 中补这些字段（属事件富化，需与决策发起方同批设计，本次不触碰，以保持与团队在途
 * 决策流改造零冲突）。
 *
 * <p><b>E-5.4 豁免</b>：本记录器仅 append 复核留痕台账（合规中间态），领域事件已由发起决策的聚合在原事务中发布， 此处不再派发领域事件。
 */
@NoDomainEvent
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimReviewRecordRecorder {

  private final ClaimReviewRecordRepository reviewRecordRepository;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onClaimDecisionMade(ClaimDecisionMadeEvent event) {
    try {
      TenantContextRunner.runAs(
          event.tenantId(),
          () ->
              reviewRecordRepository.save(
                  ClaimReviewRecord.create(
                      event.tenantId(),
                      event.claimId(),
                      null,
                      ClaimReviewRecord.ROLE_APPROVE,
                      event.reviewerId(),
                      null,
                      null,
                      event.decision().name(),
                      event.comment(),
                      event.occurredAt())));
    } catch (Exception e) {
      // 留痕落库失败不应回滚主流程；记录日志供人工 / 对账兜底。
      log.error("核赔决策留痕落库失败: claimId={}, tenantId={}", event.claimId(), event.tenantId(), e);
    }
  }
}
