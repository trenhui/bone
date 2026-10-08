package com.bone.metadata.sdk.test.domain;

import com.bone.core.annotation.Deleted;
import com.bone.core.domain.entity.Entity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 同时具备租户作用域（tenant_id）与软删（deleted）的测试实体， 专门用于覆盖聚合通道此前漏注租户/软删谓词的回归场景。 */
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Table("tenant_metric")
public class TenantMetric extends Entity<Long> {

  private String category;

  private BigDecimal amount;

  private String status;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Deleted @Builder.Default private Boolean deleted = false;
}
