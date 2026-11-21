package com.bone.procurement.governance.infrastructure;

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

/** 治理追溯条目实体：只追加，detail 为脱敏后的结构化摘要。 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_governance_trace")
public class GovernanceTraceEntity extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "capability_code")
  private String capabilityCode;

  @Column(name = "event_type")
  private String eventType;

  @Column(name = "detail")
  private String detail;

  @Column(name = "occurred_at")
  private Date occurredAt;

  @Version
  @Column(name = "version")
  private Integer version;
}
