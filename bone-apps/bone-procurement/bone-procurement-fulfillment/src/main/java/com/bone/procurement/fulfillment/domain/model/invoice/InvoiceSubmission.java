package com.bone.procurement.fulfillment.domain.model.invoice;

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

/** 供应商自助开票（810 类比）。只提交、不裁决；Finance 匹配结果经 application 回写 financeEventId。 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_invoice_submission")
public class InvoiceSubmission extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "invoice_no")
  private String invoiceNo;

  @Column(name = "vendor_party_id")
  private String vendorPartyId;

  @Column(name = "po_no")
  private String poNo;

  @Column(name = "amount")
  private BigDecimal amount;

  @Column(name = "tax")
  private BigDecimal tax;

  @Column(name = "currency")
  private String currency;

  @Column(name = "lines_json")
  private String linesJson;

  @Column(name = "status")
  private String status;

  @Column(name = "submitted_at")
  private Date submittedAt;

  @Column(name = "finance_event_id")
  private String financeEventId;

  @Version
  @Column(name = "version")
  private Integer version;
}
