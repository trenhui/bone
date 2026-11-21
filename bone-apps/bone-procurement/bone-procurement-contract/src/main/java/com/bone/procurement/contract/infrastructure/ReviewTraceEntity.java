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

/** 审查追溯条目实体：只追加不改写，detail 已脱敏。 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_review_trace")
public class ReviewTraceEntity extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "review_id")
  private String reviewId;

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
