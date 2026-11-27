package com.bone.procurement.contract.infrastructure;

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

/** Inbox 持久化实体：消费方 tenant 锚定的结论去重态，重启可恢复。 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_contract_inbox")
public class ContractReviewedInboxEntity extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "review_id")
  private String reviewId;

  @Column(name = "risk_level")
  private String riskLevel;

  @Column(name = "ai_participation")
  private String aiParticipation;

  @Column(name = "review_snapshot_ref")
  private String reviewSnapshotRef;

  @Column(name = "quarantined")
  private Boolean quarantined;

  @Version
  @Column(name = "version")
  private Integer version;
}
