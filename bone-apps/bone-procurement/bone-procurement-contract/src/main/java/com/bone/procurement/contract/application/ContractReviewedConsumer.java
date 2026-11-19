package com.bone.procurement.contract.application;

import com.bone.procurement.contract.domain.gateway.ReviewTracePort;
import com.bone.procurement.contract.domain.model.AiParticipation;
import com.bone.procurement.contract.domain.model.ContractReviewedEvent;
import com.bone.procurement.contract.domain.model.ReviewTraceEntry;
import com.bone.procurement.contract.domain.model.RiskLevel;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 消费 ContractReviewed（INBOUND，`domain.procurement.contract_reviewed.v1`）的应用用例。
 *
 * <p><b>边界（诚实声明）</b>：生产 MQ listener 未接线、属 L3。walking skeleton 由
 * ContractReviewApplicationService.override 在进程内直连调用 {@link #consume}，代替真实 MQ 的发布-消费闭环； 未来 listener
 * 只做协议解析与异常翻译后复用本方法。红线：以 reviewId 幂等（同一 id 只挂一条结论）；riskLevel=UNKNOWN 隔离转人工；降级 / 停用 /
 * 人工推翻入追溯链，快照不可变。
 */
@Component
@ConditionalOnContractAiReviewEnabled
public class ContractReviewedConsumer {

  private static final String OVERRIDE_REF_PREFIX = "snap://override/";

  private final ReviewTracePort tracePort;
  private final Map<String, StoredConclusion> conclusions = new ConcurrentHashMap<>();
  private final Set<String> quarantined = ConcurrentHashMap.newKeySet();

  public ContractReviewedConsumer(ReviewTracePort tracePort) {
    this.tracePort = tracePort;
  }

  /**
   * @return true 本次真正消费；false 重复投递已跳过。
   */
  public boolean consume(ContractReviewedEvent event) {
    StoredConclusion stored =
        new StoredConclusion(
            event.reviewId(),
            event.riskLevel(),
            event.aiAvailable(),
            event.reviewSnapshotRef(),
            event.riskLevel() == RiskLevel.UNKNOWN);
    if (conclusions.putIfAbsent(event.reviewId(), stored) != null) {
      return false;
    }
    if (stored.quarantined()) {
      quarantined.add(event.reviewId());
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
      tracePort.append(ReviewTraceEntry.of(event.reviewId(), "HUMAN_OVERRIDE", "人工推翻终决，快照不可变"));
    }
    return true;
  }

  public Optional<StoredConclusion> conclusion(String reviewId) {
    return Optional.ofNullable(conclusions.get(reviewId));
  }

  public int conclusionCount() {
    return conclusions.size();
  }

  public boolean isQuarantined(String reviewId) {
    return quarantined.contains(reviewId);
  }

  /** 幂等存储的结论摘要；快照引用只读保留，不改写。 */
  public record StoredConclusion(
      String reviewId,
      RiskLevel riskLevel,
      AiParticipation aiAvailable,
      String reviewSnapshotRef,
      boolean quarantined) {}
}
