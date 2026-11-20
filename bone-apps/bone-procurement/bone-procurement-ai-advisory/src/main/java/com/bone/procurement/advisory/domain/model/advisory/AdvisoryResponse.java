package com.bone.procurement.advisory.domain.model.advisory;

import java.time.Instant;
import java.util.Objects;

/**
 * 业务 AI 建议裁决结果（骨架，不携带任何模型输出）。
 *
 * <p>红线：{@code advisoryOnly} 恒 true（AI 仅建议、人工终决）；未登记/停用/降级/总闸关闭一律 {@code aiAvailable=UNKNOWN} 且
 * {@code degradedTo=rules_only}，主链路不中断；显式标识 + 隐式元数据双标识齐全才允许出域；证据只给引用不给 output 原文。
 */
public record AdvisoryResponse(
    String capabilityCode,
    String displayName,
    AiAvailability aiAvailable,
    boolean advisoryOnly,
    String degradedTo,
    String explicitLabel,
    String implicitMetadata,
    String evidenceRef,
    String reasonCode,
    String disclosure,
    Instant decidedAt) {

  public static final String DISCLOSURE = "本建议由 AI 辅助生成，仅供参考，最终决定由人工作出；AI 不参与资金支付、授标与准入定性。";

  public AdvisoryResponse {
    Objects.requireNonNull(capabilityCode, "capabilityCode");
    Objects.requireNonNull(aiAvailable, "aiAvailable");
    Objects.requireNonNull(reasonCode, "reasonCode");
    Objects.requireNonNull(decidedAt, "decidedAt");
  }

  public boolean hasDualLabels() {
    return explicitLabel != null
        && !explicitLabel.isBlank()
        && implicitMetadata != null
        && !implicitMetadata.isBlank();
  }

  public boolean canRelease() {
    return hasDualLabels() && advisoryOnly;
  }
}
