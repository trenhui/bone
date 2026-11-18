package com.bone.procurement.fulfillment.domain.model.exception;

import com.bone.core.annotation.Id;
import com.bone.core.annotation.Version;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 发票例外台账（IR Exception）。RESOLVED/REJECTED 只能由 Finance 事件回写。 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_ir_exception")
public class IrException extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "exception_no")
  private String exceptionNo;

  @Column(name = "invoice_no")
  private String invoiceNo;

  @Column(name = "exception_code")
  private String exceptionCode;

  @Column(name = "match_status")
  private String matchStatus;

  @Column(name = "status")
  private String status;

  @Column(name = "supplement_ref")
  private String supplementRef;

  @Column(name = "finance_event_id")
  private String financeEventId;

  @Version
  @Column(name = "version")
  private Integer version;
}
