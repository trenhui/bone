package com.bone.masterdata.domain.model.identity;

import com.bone.core.exception.DomainException;

/**
 * 跨系统实体身份映射（xref / Key Mapping）：本地 ERP / SRM 编码 ↔ 平台统一 partyId。
 *
 * <p>终局约束：本地编码不得成为业务域的身份真源；业务域只持 partyId， 通过本映射解析本地系统键。同一 (tenant, sourceSystem, localId) 只允许指向一个
 * partyId， 冲突必须 fail-closed 转人工，禁止静默覆盖（防错并主体、防串租户）。
 */
public final class EntityKeyMapping {

  private final long tenantId;
  private final String partyId;
  private final String sourceSystem;
  private final String localId;
  private final double confidence;
  private final boolean active;

  private EntityKeyMapping(
      long tenantId,
      String partyId,
      String sourceSystem,
      String localId,
      double confidence,
      boolean active) {
    this.tenantId = tenantId;
    this.partyId = partyId;
    this.sourceSystem = sourceSystem;
    this.localId = localId;
    this.confidence = confidence;
    this.active = active;
  }

  public static EntityKeyMapping of(
      long tenantId,
      String partyId,
      String sourceSystem,
      String localId,
      double confidence,
      boolean active) {
    if (isBlank(partyId) || isBlank(sourceSystem) || isBlank(localId)) {
      throw new DomainException("身份映射缺少 partyId/sourceSystem/localId");
    }
    if (confidence < 0.0 || confidence > 1.0) {
      throw new DomainException("身份映射置信度超出 [0,1]");
    }
    return new EntityKeyMapping(tenantId, partyId, sourceSystem, localId, confidence, active);
  }

  public static EntityKeyMapping active(
      long tenantId, String partyId, String sourceSystem, String localId) {
    return of(tenantId, partyId, sourceSystem, localId, 1.0, true);
  }

  /** 映射键在同一租户内唯一；tenantId 单独参与比较，绝不拼进字符串键后跨租户复用。 */
  public String localKey() {
    return sourceSystem + "|" + localId;
  }

  private static boolean isBlank(String v) {
    return v == null || v.trim().isEmpty();
  }

  public long getTenantId() {
    return tenantId;
  }

  public String getPartyId() {
    return partyId;
  }

  public String getSourceSystem() {
    return sourceSystem;
  }

  public String getLocalId() {
    return localId;
  }

  public double getConfidence() {
    return confidence;
  }

  public boolean isActive() {
    return active;
  }
}
