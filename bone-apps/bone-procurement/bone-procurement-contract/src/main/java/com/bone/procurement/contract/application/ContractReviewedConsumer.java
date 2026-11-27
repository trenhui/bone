package com.bone.procurement.contract.application;

import com.bone.procurement.contract.domain.gateway.ContractReviewTenantPort;
import com.bone.procurement.contract.domain.gateway.ContractReviewedEventStore;
import com.bone.procurement.contract.domain.gateway.ReviewTracePort;
import com.bone.procurement.contract.domain.model.review.AiParticipation;
import com.bone.procurement.contract.domain.model.review.ContractReviewedConclusion;
import com.bone.procurement.contract.domain.model.review.ContractReviewedEvent;
import com.bone.procurement.contract.domain.model.review.ReviewTraceEntry;
import com.bone.procurement.contract.domain.model.review.RiskLevel;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 消费 ContractReviewed（INBOUND，`domain.procurement.contract_reviewed.v1`）的应用用例。
 *
 * <p><b>边界（诚实声明）</b>：生产 MQ listener 未接线、属 L3。投递由显式驱动（{@link ContractReviewedEventDriver}）扫描本地
 * Outbox 后调用本方法；不伪装成生产 MQ 行为。结论与去重态持久化在 Inbox， 重启可恢复。红线：租户以消费方上下文为准，事件来源租户不一致即拒绝留痕，绝不用事件租户覆盖； 以
 * reviewId 幂等；riskLevel=UNKNOWN 隔离转人工；降级 / 停用 / 人工推翻入追溯链，快照不可变。
 */
@Component
@ConditionalOnContractAiReviewEnabled
public class ContractReviewedConsumer {

  private static final String OVERRIDE_REF_PREFIX = "snap://override/";

  private final ReviewTracePort tracePort;
  private final ContractReviewTenantPort tenantPort;
  private final ContractReviewedEventStore eventStore;

  public ContractReviewedConsumer(
      ReviewTracePort tracePort,
      ContractReviewTenantPort tenantPort,
      ContractReviewedEventStore eventStore) {
    this.tracePort = tracePort;
    this.tenantPort = tenantPort;
    this.eventStore = eventStore;
  }

  /** 消费结果：FAILED 只由驱动层在捕获异常后产生，本方法自身不吞异常。 */
  public enum ConsumeResult {
    CONSUMED,
    DUPLICATE,
    REJECTED,
    FAILED
  }

  /** 同租户直接投递（显式驱动/测试用）：来源租户即当前消费方租户。 */
  public ConsumeResult consume(ContractReviewedEvent event) {
    return consume(requireTenant(), event);
  }

  /**
   * @param sourceTenantId 事件来源租户（Outbox 记录上的 tenant_id），事件 payload 本身无 tenantId 字段。
   * @return CONSUMED 首次落库 / DUPLICATE 重复投递 / REJECTED 跨租户拒绝。
   */
  public ConsumeResult consume(String sourceTenantId, ContractReviewedEvent event) {
    String consumerTenant = requireTenant();
    if (!consumerTenant.equals(sourceTenantId)) {
      tracePort.append(
          ReviewTraceEntry.of(
              event.reviewId(),
              "CROSS_TENANT_REJECTED",
              "reviewId="
                  + event.reviewId()
                  + " 来源租户="
                  + sourceTenantId
                  + " 与消费租户="
                  + consumerTenant
                  + " 不一致，拒绝且不落库"));
      return ConsumeResult.REJECTED;
    }
    if (eventStore.inboxExists(consumerTenant, event.reviewId())) {
      return ConsumeResult.DUPLICATE;
    }

    // 先完成全部可能失败的留痕，再以条件插入作为"首次消费"提交点；失败时 Inbox 无半条结论。
    boolean quarantined = event.riskLevel() == RiskLevel.UNKNOWN;
    if (quarantined) {
      tracePort.append(
          ReviewTraceEntry.of(
              event.reviewId(), "RISK_UNKNOWN_QUARANTINE", "riskLevel=UNKNOWN，隔离转人工"));
    }
    AiParticipation ai = event.aiAvailable();
    if (ai != null && ai.isCannotConfirm()) {
      tracePort.append(
          ReviewTraceEntry.of(
              event.reviewId(), "AI_DEGRADED", "aiAvailable=" + ai.disclosureCode()));
    } else if (ai != null && ai.isAiNotParticipated()) {
      tracePort.append(ReviewTraceEntry.of(event.reviewId(), "AI_DISABLED", "AI 未参与"));
    }
    if (event.reviewSnapshotRef() != null
        && event.reviewSnapshotRef().startsWith(OVERRIDE_REF_PREFIX)) {
      tracePort.append(ReviewTraceEntry.of(event.reviewId(), "SNAPSHOT_IMMUTABLE", "人工推翻终决，快照不可变"));
    }

    boolean inserted =
        eventStore.claimInbox(
            new ContractReviewedConclusion(
                consumerTenant,
                event.reviewId(),
                event.riskLevel(),
                ai,
                event.reviewSnapshotRef(),
                quarantined));
    return inserted ? ConsumeResult.CONSUMED : ConsumeResult.DUPLICATE;
  }

  public Optional<ContractReviewedConclusion> conclusion(String reviewId) {
    return eventStore.findInbox(requireTenant(), reviewId);
  }

  public int conclusionCount() {
    return eventStore.inboxCount(requireTenant());
  }

  public boolean isQuarantined(String reviewId) {
    return conclusion(reviewId).map(ContractReviewedConclusion::quarantined).orElse(false);
  }

  /** 驱动层写 DLQ 时使用的消费方租户，绝不取事件自报。 */
  public String currentTenant() {
    return requireTenant();
  }

  private String requireTenant() {
    String tenant = tenantPort.currentTenantId();
    if (tenant == null || tenant.isBlank()) {
      throw new IllegalStateException("事件消费需要登录态租户，拒绝匿名/无租户上下文消费");
    }
    return tenant;
  }
}
