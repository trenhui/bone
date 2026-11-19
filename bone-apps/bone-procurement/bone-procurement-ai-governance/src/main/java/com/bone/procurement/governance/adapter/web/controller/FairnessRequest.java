package com.bone.procurement.governance.adapter.web.controller;

import com.bone.procurement.governance.domain.model.fairness.BiasSignal;
import jakarta.validation.constraints.NotBlank;

/** 公平性裁决请求体：特征均为受控枚举字符串，PII 禁入。 */
public record FairnessRequest(
    @NotBlank String subjectRef,
    String ownership,
    String region,
    String scale,
    String aiSuggestion,
    double aiScore) {

  public BiasSignal toSignal() {
    return new BiasSignal(
        subjectRef,
        parse(BiasSignal.OwnershipType.class, ownership),
        parse(BiasSignal.RegionTier.class, region),
        parse(BiasSignal.ScaleTier.class, scale),
        aiSuggestion,
        aiScore);
  }

  private static <E extends Enum<E>> E parse(Class<E> type, String value) {
    if (value == null || value.isBlank()) {
      return Enum.valueOf(type, "UNKNOWN");
    }
    try {
      return Enum.valueOf(type, value.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException e) {
      // 未识别枚举按 UNKNOWN fail-closed，不抛 500、不压成无偏见。
      return Enum.valueOf(type, "UNKNOWN");
    }
  }
}
