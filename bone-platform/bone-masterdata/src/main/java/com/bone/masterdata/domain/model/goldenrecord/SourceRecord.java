package com.bone.masterdata.domain.model.goldenrecord;

import com.bone.core.exception.DomainException;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** 参与实体解析的候选源记录（ERP / SRM / 工商富集 / 人工录入等）。 */
public final class SourceRecord {

  private final String recordId;
  private final String sourceSystem;
  private final long tenantId;

  /** 数值越小可信度越高；同一来源内稳定。 */
  private final int trustRank;

  private final Instant observedAt;
  private final Map<String, String> values;

  private SourceRecord(
      String recordId,
      String sourceSystem,
      long tenantId,
      int trustRank,
      Instant observedAt,
      Map<String, String> values) {
    this.recordId = recordId;
    this.sourceSystem = sourceSystem;
    this.tenantId = tenantId;
    this.trustRank = trustRank;
    this.observedAt = observedAt;
    this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }

  public static SourceRecord of(
      String recordId,
      String sourceSystem,
      long tenantId,
      int trustRank,
      Instant observedAt,
      Map<String, String> values) {
    if (isBlank(recordId) || isBlank(sourceSystem) || observedAt == null || values == null) {
      throw new DomainException("候选源记录缺少必填身份/观测信息");
    }
    return new SourceRecord(recordId, sourceSystem, tenantId, trustRank, observedAt, values);
  }

  /** 空值视为「无意见」，不得覆盖已有非空值。 */
  public Optional<String> valueOf(String fieldCode) {
    String v = values.get(fieldCode);
    return v == null || v.trim().isEmpty() ? Optional.empty() : Optional.of(v);
  }

  private static boolean isBlank(String v) {
    return v == null || v.trim().isEmpty();
  }

  public String getRecordId() {
    return recordId;
  }

  public String getSourceSystem() {
    return sourceSystem;
  }

  public long getTenantId() {
    return tenantId;
  }

  public int getTrustRank() {
    return trustRank;
  }

  public Instant getObservedAt() {
    return observedAt;
  }

  public Map<String, String> getValues() {
    return values;
  }
}
