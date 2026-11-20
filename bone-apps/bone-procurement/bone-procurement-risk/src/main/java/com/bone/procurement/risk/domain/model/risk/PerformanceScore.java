package com.bone.procurement.risk.domain.model.risk;

import com.bone.core.annotation.Id;
import com.bone.core.annotation.Version;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 供应商绩效评分（只读投影表映射）。
 *
 * <p>权重规则、自动授信、外部征信接入属 owner 红线，本骨架只登记分数与证据引用，不产出授信或淘汰决定。
 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_performance_score")
public class PerformanceScore extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "supplier_id")
  private String supplierId;

  @Column(name = "period")
  private String period;

  @Column(name = "delivery_score")
  private BigDecimal deliveryScore;

  @Column(name = "quality_score")
  private BigDecimal qualityScore;

  @Column(name = "cost_score")
  private BigDecimal costScore;

  @Column(name = "compliance_score")
  private BigDecimal complianceScore;

  @Column(name = "total_score")
  private BigDecimal totalScore;

  @Column(name = "evidence_ref")
  private String evidenceRef;

  @Version
  @Column(name = "version")
  private Integer version;

  /** 禁止把评分直接当授信/淘汰决定（owner 红线：权重未冻结、AI 不得自动裁决）。 */
  public boolean canDecideSupplierAutomatically() {
    return false;
  }
}
