package com.bone.claim.domain.model.evaluation;

import com.bone.claim.domain.model.inspection.event.InspectionConcludedEvent;
import com.bone.core.domain.DomainEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * 后督差异回流样本（学习层最小闭环）。
 *
 * <p>人工复核结论是唯一可信的标注源。质检给出 DIFF 时，本事件把差异按归因分类为三类候选： 模型评测集 / 规则修正 / 控制修正，并带幂等指纹（同一次抽检重复投递可被消费方去重）。
 * 差异明细仍留在 {@code t_claim_inspection.diff_detail}，按 {@code inspectionNo} 回查即可，
 * 事件只作索引与触发器，避免把长文本复制进消息。
 *
 * <p><b>一期边界</b>：事件经进程内领域事件总线投递，由既有 outbox 机制兜底； 评测集消费方（bone-ai C7）、独立盲标与阈值校准属 Phase
 * B/V2，文档不得先于代码宣告闭环完成。
 */
public record AiEvalSampleCollectedEvent(
    Long inspectionId,
    Long tenantId,
    Long claimId,
    String inspectionNo,
    String diffType,
    SampleType sampleType,
    String action,
    String fingerprint,
    Instant occurredAt)
    implements DomainEvent {

  /** 回流目标（受控枚举，禁自由文本）：决定样本进哪条改进回路。 */
  public enum SampleType {
    /** AI 误判 → 评测集 / 独立盲标。 */
    EVAL_SET,
    /** 理算差异、规则漏命中 → 规则修正候选。 */
    RULE_FIX,
    /** 额度/控制异常 → 控制修正候选。 */
    CONTROL_FIX
  }

  public static AiEvalSampleCollectedEvent from(InspectionConcludedEvent event) {
    SampleType type = sampleTypeOf(event.diffType());
    return new AiEvalSampleCollectedEvent(
        event.inspectionId(),
        event.tenantId(),
        event.claimId(),
        event.inspectionNo(),
        event.diffType() == null ? "" : event.diffType().name(),
        type,
        event.action() == null ? "" : event.action().name(),
        fingerprintOf(event),
        event.occurredAt() == null ? Instant.now() : event.occurredAt());
  }

  private static SampleType sampleTypeOf(
      com.bone.claim.domain.model.inspection.InspectionDiffType diffType) {
    if (diffType == null) {
      return SampleType.RULE_FIX;
    }
    return switch (diffType) {
      case AI_MISJUDGE -> SampleType.EVAL_SET;
      case QUOTA_ABNORMAL -> SampleType.CONTROL_FIX;
      default -> SampleType.RULE_FIX;
    };
  }

  /** 幂等指纹：同租户 + 同抽检单 + 同差异类型只应产生一个样本。 */
  private static String fingerprintOf(InspectionConcludedEvent event) {
    String raw = event.tenantId() + ":" + event.inspectionNo() + ":" + event.diffType();
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(16);
      for (int i = 0; i < 8; i++) {
        hex.append(String.format("%02x", digest[i]));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 不可用，无法生成评测样本指纹", ex);
    }
  }
}
