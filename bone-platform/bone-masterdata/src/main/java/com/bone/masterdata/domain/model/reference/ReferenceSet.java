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
 * 参考数据值域（G15，§2.3）：币种/地区/行业/计量单位等，不走审批流。
 *
 * <p><b>2026-09-26 裁决（多租户规范 §8 约束 6）</b>：值域是「平台只写（{@code reference:write} 写路径由应用服务按
 * 当前租户守卫）、多方只读」的<strong>全局目录</strong>，聚合不映射 tenant_id（非租户作用域，与 {@code mdm_domain_template}
 * 同构）；租户不可建/改/归档值域，只可扩展私有值（{@link TenantReferenceValue}）。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("mdm_reference_set")
public class ReferenceSet extends AggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  /** 值域编码：CURRENCY/COUNTRY/INDUSTRY/UOM/...，全局唯一（平台目录）。 */
  @Column(name = "set_code")
  private String setCode;

  @Column(name = "set_name")
  private String setName;

  /** 外部标准：ISO4217/GB2260 等。 */
  @Column(name = "external_standard")
  private String externalStandard;

  private String description;

  /** 状态：DRAFT / PUBLISHED / ARCHIVED。 */
  private String status;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static ReferenceSet create(
      Long id, String setCode, String setName, String externalStandard, String description) {
    ReferenceSet set = new ReferenceSet();
    set.id = id;
    set.setCode = setCode;
    set.setName = setName;
    set.externalStandard = externalStandard;
    set.description = description;
    set.status = "PUBLISHED";
    set.createdAt = LocalDateTime.now();
    set.updatedAt = LocalDateTime.now();
    return set;
  }

  public void update(String setName, String externalStandard, String description) {
    this.setName = setName;
    this.externalStandard = externalStandard;
    this.description = description;
    this.updatedAt = LocalDateTime.now();
  }

  public void archive() {
    this.status = "ARCHIVED";
    this.updatedAt = LocalDateTime.now();
  }
}
