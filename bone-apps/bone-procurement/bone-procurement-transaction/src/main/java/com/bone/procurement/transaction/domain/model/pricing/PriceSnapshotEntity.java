package com.bone.procurement.transaction.domain.model.pricing;

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

/** 价格快照实体（只追加不可变）。应用层不提供 update/delete；重复下达同版本直接复用既有行。 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_price_snapshot")
public class PriceSnapshotEntity extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "po_no")
  private String poNo;

  @Column(name = "po_version")
  private Integer poVersion;

  @Column(name = "lines_json")
  private String linesJson;

  @Column(name = "tax_amount")
  private BigDecimal taxAmount;

  @Column(name = "currency")
  private String currency;

  @Column(name = "fx_rate")
  private BigDecimal fxRate;

  @Column(name = "fx_as_of")
  private Date fxAsOf;

  @Column(name = "fx_source")
  private String fxSource;

  @Column(name = "rounding_mode")
  private String roundingMode;

  @Column(name = "tax_code_version")
  private String taxCodeVersion;

  @Column(name = "computed_hash")
  private String computedHash;

  @Column(name = "computed_at")
  private Date computedAt;

  @Version
  @Column(name = "version")
  private Integer version;
}
