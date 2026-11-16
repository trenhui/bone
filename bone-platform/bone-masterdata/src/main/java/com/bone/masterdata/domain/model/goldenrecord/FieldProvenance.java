package com.bone.masterdata.domain.model.goldenrecord;

import com.bone.core.exception.DomainException;
import java.time.Instant;
import java.util.Objects;

/**
 * 字段级 lineage：Golden Record 中每个胜出字段必须能追溯到唯一源记录与胜出规则。
 *
 * <p>终局约束：缺任一条 lineage 即整笔合并无效，不允许「部分字段可溯源」的黄金记录进入分发链路。 这是误合并可整笔回滚、以及字段 Owner 争议可仲裁的前提（对齐
 * DAMA-DMBOK 数据血缘与 SAP MDG survivorship 的逐字段留痕）。
 *
 * <p>本类型是纯领域值对象，不继承聚合根、不携带 SDK 持久化注解：持久化形态由后续 ApplicationService / DDL 决定，领域语义不随 adapter 变化。
 */
public final class FieldProvenance {

  private final String fieldCode;
  private final String sourceRecordId;
  private final String sourceSystem;
  private final String ruleId;
  private final Instant observedAt;

  /** 仅 STEWARD_OVERRIDE 胜出时非空，记录具名数据管家。 */
  private final String stewardAccountId;

  private FieldProvenance(
      String fieldCode,
      String sourceRecordId,
      String sourceSystem,
      String ruleId,
      Instant observedAt,
      String stewardAccountId) {
    this.fieldCode = fieldCode;
    this.sourceRecordId = sourceRecordId;
    this.sourceSystem = sourceSystem;
    this.ruleId = ruleId;
    this.observedAt = observedAt;
    this.stewardAccountId = stewardAccountId;
  }

  public static FieldProvenance of(
      String fieldCode,
      String sourceRecordId,
      String sourceSystem,
      String ruleId,
      Instant observedAt,
      String stewardAccountId) {
    FieldProvenance p =
        new FieldProvenance(
            fieldCode, sourceRecordId, sourceSystem, ruleId, observedAt, stewardAccountId);
    p.requireComplete();
    return p;
  }

  /** 沿用合并前已存在的字段值及其 lineage（不虚构新来源）。 */
  public static FieldProvenance inherit(FieldProvenance original, String fieldCode) {
    Objects.requireNonNull(original, "original lineage");
    return of(
        fieldCode,
        original.sourceRecordId,
        original.sourceSystem,
        original.ruleId,
        original.observedAt,
        original.stewardAccountId);
  }

  /** 完整性硬校验：任何一项缺失都使整笔合并失败，而不是降级为「部分可溯源」。 */
  public void requireComplete() {
    if (isBlank(fieldCode)
        || isBlank(sourceRecordId)
        || isBlank(sourceSystem)
        || isBlank(ruleId)
        || observedAt == null) {
      throw new DomainException("字段级 lineage 不完整，整笔合并必须拒绝: " + fieldCode);
    }
  }

  private static boolean isBlank(String v) {
    return v == null || v.trim().isEmpty();
  }

  public String getFieldCode() {
    return fieldCode;
  }

  public String getSourceRecordId() {
    return sourceRecordId;
  }

  public String getSourceSystem() {
    return sourceSystem;
  }

  public String getRuleId() {
    return ruleId;
  }

  public Instant getObservedAt() {
    return observedAt;
  }

  public String getStewardAccountId() {
    return stewardAccountId;
  }
}
