package com.bone.masterdata.domain.model.reference;

import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 参考数据租户私有扩展值（G15，§2.3）——overlay 拆分的租户层（2026-09-26 裁决，多租户规范 §8 约束 6）。
 *
 * <p>租户在平台值域下扩展的私有值（如行业码表的租户细分行业），租户作用域聚合：SDK 按当前租户严格过滤/回填， 他租户私有值互不可见。写路径由 {@code
 * ReferenceDataApplicationService} 按当前租户分发：平台管理员（tenant=0） 写平台值表 {@link ReferenceValue}，租户写本表。
 *
 * <p>约束：{@code value_code} 不得与同值域下的平台值或其他租户已用编码冲突（应用服务跨表查重；DDL 层 {@code uk(tenant_id, set_id,
 * value_code)} 只保证租户内唯一）。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("mdm_reference_value_tenant")
public class TenantReferenceValue extends TenantAggregateRoot<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  /** 所属值域ID（引用平台值域 {@code mdm_reference_set.id}，值域全局可见）。 */
  @Column(name = "set_id")
  private Long setId;

  @Column(name = "value_code")
  private String valueCode;

  @Column(name = "value_name")
  private String valueName;

  /** 外部标准码（ISO/GB），租户私有值一般无对应外部标准，可空。 */
  @Column(name = "external_code")
  private String externalCode;

  @Column(name = "sort_order")
  private Integer sortOrder;

  private Boolean enabled;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static TenantReferenceValue create(
      Long id,
      Long setId,
      String valueCode,
      String valueName,
      String externalCode,
      Integer sortOrder) {
    TenantReferenceValue value = new TenantReferenceValue();
    value.id = id;
    value.setId = setId;
    value.valueCode = valueCode;
    value.valueName = valueName;
    value.externalCode = externalCode;
    value.sortOrder = sortOrder == null ? 0 : sortOrder;
    value.enabled = true;
    value.createdAt = LocalDateTime.now();
    value.updatedAt = LocalDateTime.now();
    return value;
  }

  public void update(String valueName, String externalCode, Integer sortOrder) {
    this.valueName = valueName;
    this.externalCode = externalCode;
    if (sortOrder != null) {
      this.sortOrder = sortOrder;
    }
    this.updatedAt = LocalDateTime.now();
  }

  public void disable() {
    this.enabled = false;
    this.updatedAt = LocalDateTime.now();
  }

  public void enable() {
    this.enabled = true;
    this.updatedAt = LocalDateTime.now();
  }
}
