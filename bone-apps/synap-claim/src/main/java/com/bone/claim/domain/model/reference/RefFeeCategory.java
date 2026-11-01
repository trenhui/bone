package com.bone.claim.domain.model.reference;

import com.bone.core.domain.TenantAggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.Instant;
import lombok.Getter;

/**
 * 基础目录——费用类别（{@code t_ref_fee_category}，租户级主数据，0094）。
 *
 * <p>标准 10 类（床位/检查/治疗/手术/药品/材料/护理/诊查/化验/其他）由种子播种； 责任费用规则（{@code t_adj_liability_fee_rule}）的
 * fee_code 必须取自本表——服务层校验存在性， 防止"配了费用规则、理算归类时找不到口径"。
 */
@Getter
@Table("t_ref_fee_category")
public class RefFeeCategory extends TenantAggregateRoot<Long> {

  @Column(name = "fee_code")
  private String feeCode;

  @Column(name = "fee_name")
  private String feeName;

  @Column(name = "sort_no")
  private Integer sortNo;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version")
  private Long version;

  public static RefFeeCategory of(
      Long id, Long tenantId, String feeCode, String feeName, Integer sortNo) {
    RefFeeCategory e = new RefFeeCategory();
    e.setId(id);
    e.setTenantId(tenantId);
    e.feeCode = feeCode == null ? "" : feeCode.trim();
    e.feeName = feeName == null ? "" : feeName.trim();
    e.sortNo = sortNo == null ? 0 : sortNo;
    Instant now = Instant.now();
    e.createdAt = now;
    e.updatedAt = now;
    return e;
  }

  public void applyEdit(String feeName, Integer sortNo) {
    this.feeName = feeName == null ? this.feeName : feeName.trim();
    this.sortNo = sortNo == null ? this.sortNo : sortNo;
    this.updatedAt = Instant.now();
  }
}
