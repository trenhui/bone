package com.bone.claim.application;

import com.bone.claim.domain.model.claim.event.ClaimDecisionMadeEvent;
import com.bone.claim.domain.model.claim.valueobject.Decision;
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
 * <p>这是「大额双人复核 / 权限校验」的不可篡改事实来源：谁在何时以什么依据批准/驳回了一笔赔付。 {@code ClaimCase.decide} 是<b>人工核赔初审</b>（设
 * firstReviewerId、41→42），故本记录 role 必须是 {@link ClaimReviewRecord#ROLE_REVIEW}（核赔初审）；{@code
 * ROLE_SECOND_REVIEW} 为双人复核、{@code ROLE_APPROVE} 为调账审批，各有其动作。role 写错会令 §11.7 不变量 5（结算单 PAID 前须存在
 * REVIEW 与 SECOND_REVIEW 两条记录） 的事实来源失真，且撞上 DDL 唯一键 {@code uk_claim_role_reviewer(claim_id, role,
 * reviewer_id)}。
 *
 * <p><b>decision 显式映射</b>：不用 {@code Decision.name()} 直接落库，避免枚举名与 {@code DECISION_*} 字符串常量
 * 隐式耦合（任一侧改名会静默漂移、审计事实源写下下游不认的值）。改用穷举 switch 显式映射，新增枚举值未同步时 default 抛错（被外层兜底日志捕获，不回滚主流程），并由 {@code
 * mapsAllDecisionEnumValuesAndWritesReviewRole} 以 save 次数守护完备性。
 *
 * <p>字段来源：claimId / tenantId / reviewerId / decision / comment 直接取自事件；operatorId（发起人）、
 * amount（核定金额）、settlement_no（结算单号）事件未携带，按空 / 零默认——若后续需完整对账，应在 {@code ClaimDecisionMadeEvent}
 * 中补这些字段（属事件富化，需与决策发起方同批设计，本次不触碰，以保持与团队在途 决策流改造零冲突）。
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
                      ClaimReviewRecord.ROLE_REVIEW,
                      event.reviewerId(),
                      null,
                      null,
                      toDecisionCode(event.decision()),
                      event.comment(),
                      event.occurredAt())));
    } catch (Exception e) {
      // 留痕落库失败不应回滚主流程；[AUDIT_WRITE_FAILED] 结构化标记供合规监控告警 + 人工 / 对账兜底。
      log.error(
          "[AUDIT_WRITE_FAILED] review_record claimId={} tenantId={} event={}",
          event.claimId(),
          event.tenantId(),
          event.getClass().getSimpleName(),
          e);
    }
  }

  /**
   * 决策枚举 → 审计受控码的显式映射。新增 {@link Decision} 枚举值而漏改本方法时 default fail-fast， 使问题以结构化日志 +
   * 完备性测试暴露，而非静默写入下游不认的 decision 值。
   */
  private static String toDecisionCode(Decision decision) {
    return switch (decision) {
      case APPROVED -> ClaimReviewRecord.DECISION_APPROVED;
      case REJECTED -> ClaimReviewRecord.DECISION_REJECTED;
      case REQUEST_MORE_EVIDENCE -> ClaimReviewRecord.DECISION_REQUEST_MORE_EVIDENCE;
      default -> throw new IllegalArgumentException("未映射的核赔决策枚举值: " + decision);
    };
  }
}
