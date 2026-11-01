package com.bone.claim.domain.model.reference;

import com.bone.core.domain.TenantAggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.Instant;
import lombok.Getter;

/** 基础目录——疾病（{@code t_ref_disease}，租户级主数据，0094；编码为 ICD-10）。 */
@Getter
@Table("t_ref_disease")
public class RefDisease extends TenantAggregateRoot<Long> {

  @Column(name = "disease_code")
  private String diseaseCode;

  @Column(name = "disease_name")
  private String diseaseName;

  /** ICD 类目（如 循环系统I），便于分类浏览。 */
  @Column(name = "icd_category")
  private String icdCategory;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version")
  private Long version;

  public static RefDisease of(
      Long id, Long tenantId, String diseaseCode, String diseaseName, String icdCategory) {
    RefDisease e = new RefDisease();
    e.setId(id);
    e.setTenantId(tenantId);
    e.diseaseCode = diseaseCode == null ? "" : diseaseCode.trim();
    e.diseaseName = diseaseName == null ? "" : diseaseName.trim();
    e.icdCategory = icdCategory == null ? "" : icdCategory.trim();
    Instant now = Instant.now();
    e.createdAt = now;
    e.updatedAt = now;
    return e;
  }

  public void applyEdit(String diseaseName, String icdCategory) {
    this.diseaseName = diseaseName == null ? this.diseaseName : diseaseName.trim();
    this.icdCategory = icdCategory == null ? "" : icdCategory.trim();
    this.updatedAt = Instant.now();
  }
}
