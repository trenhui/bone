package com.bone.procurement.transaction.domain.model.po;

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

/** 采购订单聚合根。金额强一致：po_amount = Σ line_amount（由应用服务在构造时保证）。 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_po")
public class PurchaseOrder extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "po_no")
  private String poNo;

  @Column(name = "pr_no")
  private String prNo;

  @Column(name = "org_id")
  private Long orgId;

  @Column(name = "vendor_party_id")
  private String vendorPartyId;

  @Column(name = "legal_entity_id")
  private Long legalEntityId;

  @Column(name = "status")
  private String status;

  @Column(name = "po_amount")
  private BigDecimal poAmount;

  @Column(name = "currency")
  private String currency;

  @Column(name = "tax_amount")
  private BigDecimal taxAmount;

  @Column(name = "fx_rate")
  private BigDecimal fxRate;

  @Column(name = "fx_as_of")
  private Date fxAsOf;

  @Column(name = "fx_source")
  private String fxSource;

  @Column(name = "tax_code_version")
  private String taxCodeVersion;

  @Column(name = "budget_ref")
  private String budgetRef;

  @Version
  @Column(name = "version")
  private Integer version;
}
