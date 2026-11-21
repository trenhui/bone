package com.bone.procurement.governance.infrastructure;

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

/** AI 能力登记实体：状态 + 具名责任人 + 追溯结构化摘要（不存模型输出原文）。 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_ai_capability")
public class AiCapabilityEntity extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "code")
  private String code;

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "state")
  private String state;

  @Column(name = "owner_user_id")
  private String ownerUserId;

  @Column(name = "owner_display_name")
  private String ownerDisplayName;

  @Column(name = "trace_json")
  private String traceJson;

  @Version
  @Column(name = "version")
  private Integer version;
}
