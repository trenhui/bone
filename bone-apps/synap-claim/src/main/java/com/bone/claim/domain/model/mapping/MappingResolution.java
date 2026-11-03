package com.bone.claim.domain.model.mapping;

/**
 * 解析结果。
 *
 * <p><b>{@code standardCode} 只在 {@link ResolutionStatus#RESOLVED} 时非空</b>，这是本类唯一的硬约束：
 * 未命中若回退成空串，下游会把它当"合法但空"的编码写进明细，金额链路不会报错、只会算错。用构造期断言把 这条约束钉死，比在每个调用点写 if 可靠。
 *
 * @param status 解析结局
 * @param standardCode 标准码（仅 RESOLVED 非空）
 * @param mappingId 命中的映射行 ID（仅 RESOLVED 非空，用于追溯"当时按哪条映射翻译"）
 * @param reasonCode 未命中/冲突的理由码，直接落队列与日志
 */
public record MappingResolution(
    ResolutionStatus status, String standardCode, Long mappingId, String reasonCode) {

  public static final String RAW_CODE_BLANK = "RAW_CODE_BLANK";
  public static final String NO_ACTIVE_MAPPING = "NO_ACTIVE_MAPPING";
  public static final String DISAMBIGUATION_REQUIRED = "DISAMBIGUATION_REQUIRED";
  public static final String DISAMBIGUATION_NOT_MATCHED = "DISAMBIGUATION_NOT_MATCHED";
  public static final String SPLIT_CODE = "SPLIT_CODE";
  public static final String ALIAS_CYCLE = "ALIAS_CYCLE";

  public MappingResolution {
    if (status == null) {
      throw new IllegalArgumentException("解析状态不能为空");
    }
    if (status == ResolutionStatus.RESOLVED) {
      if (standardCode == null || standardCode.isBlank()) {
        throw new IllegalArgumentException("RESOLVED 必须携带非空标准码");
      }
    } else if (standardCode != null) {
      throw new IllegalArgumentException("非 RESOLVED 不得携带标准码（否则下游会把空值/原始码当合法值使用）: " + status);
    }
  }

  public static MappingResolution resolved(String standardCode, Long mappingId) {
    return new MappingResolution(ResolutionStatus.RESOLVED, standardCode, mappingId, "");
  }

  public static MappingResolution missing(String reasonCode) {
    return new MappingResolution(ResolutionStatus.MISSING, null, null, reasonCode);
  }

  public static MappingResolution ambiguous(String reasonCode) {
    return new MappingResolution(ResolutionStatus.AMBIGUOUS, null, null, reasonCode);
  }

  public static MappingResolution split() {
    return new MappingResolution(ResolutionStatus.SPLIT_REQUIRES_MANUAL, null, null, SPLIT_CODE);
  }

  public static MappingResolution aliasCycle() {
    return new MappingResolution(ResolutionStatus.ALIAS_CYCLE, null, null, ALIAS_CYCLE);
  }
}
