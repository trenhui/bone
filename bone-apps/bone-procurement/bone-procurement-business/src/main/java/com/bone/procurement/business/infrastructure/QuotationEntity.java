package com.bone.procurement.business.infrastructure;

import com.bone.core.annotation.Id;
import com.bone.core.annotation.Version;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 报价持久化事实；AI 字段只存引用/建议，不存模型 output 原文。 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_quotation")
public class QuotationEntity extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "quote_id")
  private String quoteId;

  @Column(name = "rfx_no")
  private String rfxNo;

  @Column(name = "supplier_id")
  private String supplierId;

  @Column(name = "amount")
  private BigDecimal amount;

  @Column(name = "currency")
  private String currency;

  @Column(name = "valid_until")
  private Date validUntil;

  @Column(name = "ai_score")
  private BigDecimal aiScore;

  @Column(name = "ai_recommendation")
  private String aiRecommendation;

  @Version
  @Column(name = "version")
  private Integer version;
}
