package com.bone.claim.domain.model.reference;

import com.bone.core.domain.TenantAggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.Instant;
import lombok.Getter;

/** 基础目录——药品（{@code t_ref_drug}，租户级主数据，0094；甲/乙/丙类 + 社保内外）。 */
@Getter
@Table("t_ref_drug")
public class RefDrug extends TenantAggregateRoot<Long> {

  @Column(name = "drug_code")
  private String drugCode;

  @Column(name = "drug_name")
  private String drugName;

  /** 社保内（1是/0否）。 */
  @Column(name = "social_flag")
  private Integer socialFlag;

  /** 医保分类（甲类/乙类/丙类）。 */
  @Column(name = "medical_type")
  private String medicalType;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version")
  private Long version;

  public static RefDrug of(
      Long id,
      Long tenantId,
      String drugCode,
      String drugName,
      Integer socialFlag,
      String medicalType) {
    RefDrug e = new RefDrug();
    e.setId(id);
    e.setTenantId(tenantId);
    e.drugCode = drugCode == null ? "" : drugCode.trim();
    e.drugName = drugName == null ? "" : drugName.trim();
    e.socialFlag = socialFlag == null ? 1 : socialFlag;
    e.medicalType = medicalType == null ? "" : medicalType.trim();
    Instant now = Instant.now();
    e.createdAt = now;
    e.updatedAt = now;
    return e;
  }

  public void applyEdit(String drugName, Integer socialFlag, String medicalType) {
    this.drugName = drugName == null ? this.drugName : drugName.trim();
    this.socialFlag = socialFlag == null ? this.socialFlag : socialFlag;
    this.medicalType = medicalType == null ? "" : medicalType.trim();
    this.updatedAt = Instant.now();
  }
}
