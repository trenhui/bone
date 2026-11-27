package com.bone.procurement.contract.infrastructure;

import com.bone.core.annotation.Id;
import com.bone.core.annotation.Version;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Outbox 持久化实体：payload 只存 7 字段快照（禁 output 原文 / PII / 合同正文）。 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_contract_outbox")
public class ContractReviewedOutboxEntity extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "review_id")
  private String reviewId;

  @Column(name = "payload_json")
  private String payloadJson;

  @Column(name = "delivery_status")
  private String deliveryStatus;

  @Column(name = "delivered_at")
  private Date deliveredAt;

  @Version
  @Column(name = "version")
  private Integer version;
}
