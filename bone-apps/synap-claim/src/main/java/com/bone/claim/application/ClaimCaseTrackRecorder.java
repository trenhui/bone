package com.bone.claim.application;

import com.bone.claim.domain.model.claim.ClaimCaseTrack;
import com.bone.claim.domain.model.claim.event.ClaimStatusChangedEvent;
import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import com.bone.claim.domain.repository.ClaimCaseTrackRepository;
import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.tenant.context.TenantContextRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 案件状态流转流水记录器（ADR-003 事件溯源）。
 *
 * <p>订阅进程内 {@link ClaimStatusChangedEvent}（聚合每次合法状态跃迁必发，§6.2），在事务提交后 append 一笔 {@code
 * t_claim_case_track}。写路径显式 {@link TenantContextRunner#runAs} 声明租户（AFTER_COMMIT 后线程上下文已清空， 写路径
 * ADR-0029 失败关闭须显式租户，否则漏落）。
 *
 * <p>该流水是 {@code claim_case_sla} 物化读模型（ADR-003 / §11.7 由调度按租户物化）的来源，并支撑按租户审计与对账。
 *
 * <p><b>E-5.4 豁免</b>：本记录器 {@code save()} 仅写 append-only 状态流水台账（技术中间态），领域事件已由发起跃迁的聚合在原
 * 事务中发布，此处不再派发领域事件。
 */
@NoDomainEvent
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimCaseTrackRecorder {

  private final ClaimCaseTrackRepository trackRepository;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onClaimStatusChanged(ClaimStatusChangedEvent event) {
    try {
      TenantContextRunner.runAs(
          event.tenantId(),
          () ->
              trackRepository.save(
                  ClaimCaseTrack.of(
                      event.tenantId(),
                      event.claimId(),
                      event.fromStatus().name(),
                      event.toStatus().name(),
                      resolveStage(event.toStatus()),
                      event.actionCode(),
                      event.operatorId(),
                      null,
                      "状态跃迁 " + event.fromStatus().name() + "->" + event.toStatus().name())));
    } catch (Exception e) {
      // 流水落库失败不应回滚主流程；记录日志供人工/对账兜底。
      log.error("状态流转流水落库失败: claimId={}, tenantId={}", event.claimId(), event.tenantId(), e);
    }
  }

  /** 由跃迁后的状态枚举粗略归类阶段（SUBMIT/REVIEW/SETTLE/CLOSE/APPEAL）。未识别码记 UNKNOWN，不影响流水主字段。 */
  private String resolveStage(ClaimStatus toStatus) {
    if (toStatus == null) {
      return "UNKNOWN";
    }
    return switch (toStatus) {
      case DRAFT, ACCEPTED, EVIDENCE_SUBMITTING -> "SUBMIT";
      case PENDING_PRE_EXAM, PRE_EXAMINING, AUTO_PRE_EXAMINING, PRE_EXAMINED, REVIEWING -> "REVIEW";
      case PENDING_ADJUST, ADJUSTING, ADJUSTED -> "SETTLE";
      case CLOSED_REJECTED, FINISHED -> "CLOSE";
      case APPEALING -> "APPEAL";
      default -> "UNKNOWN";
    };
  }
}
