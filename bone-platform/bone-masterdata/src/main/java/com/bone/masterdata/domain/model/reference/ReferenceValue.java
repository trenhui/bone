package com.bone.masterdata.domain.model.reference;

import com.bone.core.annotation.Id;
import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 参考数据值（G15）——<strong>平台值</strong>：值域下的标准值（如 CURRENCY → CNY/USD），平台域维护。
 *
 * <p><b>2026-09-26 裁决（多租户规范 §8 约束 6）</b>：混存表按 overlay 模式拆分——本聚合对应平台值表（非租户作用域）， 租户私有扩展值落在 {@link
 * TenantReferenceValue}（{@code mdm_reference_value_tenant}）。{@code uk(set_id, value_code)}
 * 保证平台值编码在值域内全局唯一，租户私有值不得与平台值重码（应用服务跨表校验）——读取时按 值域合并即纯并集，无覆盖（shadowing）歧义。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("mdm_reference_value")
public class ReferenceValue extends AggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  /** 所属值域ID。 */
  @Column(name = "set_id")
  private Long setId;

  @Column(name = "value_code")
  private String valueCode;

  @Column(name = "value_name")
  private String valueName;

  /** 外部标准码（ISO/GB）。 */
  @Column(name = "external_code")
  private String externalCode;

  @Column(name = "sort_order")
  private Integer sortOrder;

  private Boolean enabled;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static ReferenceValue create(
      Long id,
      Long setId,
      String valueCode,
      String valueName,
      String externalCode,
      Integer sortOrder) {
    ReferenceValue value = new ReferenceValue();
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
