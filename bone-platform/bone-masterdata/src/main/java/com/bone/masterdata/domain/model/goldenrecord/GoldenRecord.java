package com.bone.masterdata.domain.model.goldenrecord;

import com.bone.core.exception.DomainException;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 平台侧黄金记录快照（不可变）。
 *
 * <p>终局语义：平台对「身份 + General data」是唯一 system of entry，Golden Record 由共享服务层 产生；采购等业务域只持有 partyId
 * 与自有业务视图列，不得另建第二份 Golden Record。
 *
 * <p>硬不变量：每个有值字段都必须有完整字段级 lineage；否则构造即失败（整笔拒绝， 而不是产出部分可溯源的黄金记录）。
 */
public final class GoldenRecord {

  private final long tenantId;

  /** 平台统一身份 ID，终局不变，域侧不得据此反推本地编码。 */
  private final String partyId;

  private final long version;
  private final Map<String, String> values;
  private final Map<String, FieldProvenance> provenance;
  private final Instant mergedAt;

  private GoldenRecord(
      long tenantId,
      String partyId,
      long version,
      Map<String, String> values,
      Map<String, FieldProvenance> provenance,
      Instant mergedAt) {
    this.tenantId = tenantId;
    this.partyId = partyId;
    this.version = version;
    this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    this.provenance = Collections.unmodifiableMap(new LinkedHashMap<>(provenance));
    this.mergedAt = mergedAt;
  }

  public static GoldenRecord of(
      long tenantId,
      String partyId,
      long version,
      Map<String, String> values,
      Map<String, FieldProvenance> lineage,
      Instant mergedAt) {
    if (partyId == null || partyId.trim().isEmpty()) {
      throw new DomainException("Golden Record 缺少 partyId");
    }
    if (version <= 0 || mergedAt == null || values == null || lineage == null) {
      throw new DomainException("Golden Record 缺少版本/时间/字段集合");
    }
    for (Map.Entry<String, String> e : values.entrySet()) {
      FieldProvenance p = lineage.get(e.getKey());
      if (p == null || !e.getKey().equals(p.getFieldCode())) {
        throw new DomainException("字段缺少 lineage，整笔合并拒绝: " + e.getKey());
      }
      p.requireComplete();
    }
    return new GoldenRecord(tenantId, partyId, version, values, lineage, mergedAt);
  }

  public String getValue(String fieldCode) {
    return values.get(fieldCode);
  }

  public FieldProvenance getProvenance(String fieldCode) {
    return provenance.get(fieldCode);
  }

  public Set<String> fieldCodes() {
    return values.keySet();
  }

  public long getTenantId() {
    return tenantId;
  }

  public String getPartyId() {
    return partyId;
  }

  public long getVersion() {
    return version;
  }

  public Map<String, String> getValues() {
    return values;
  }

  public Map<String, FieldProvenance> getProvenance() {
    return provenance;
  }

  public Instant getMergedAt() {
    return mergedAt;
  }
}
