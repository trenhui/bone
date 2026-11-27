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

/** DLQ 持久化实体：失败原因 + 原始 payload 引用 + 重放状态，按 review_id 定位。 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_contract_dlq")
public class ContractReviewedDlqEntity extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "review_id")
  private String reviewId;

  @Column(name = "failure_reason")
  private String failureReason;

  @Column(name = "outbox_ref")
  private String outboxRef;

  @Column(name = "resolved")
  private Boolean resolved;

  @Column(name = "occurred_at")
  private Date occurredAt;

  @Version
  @Column(name = "version")
  private Integer version;
}
