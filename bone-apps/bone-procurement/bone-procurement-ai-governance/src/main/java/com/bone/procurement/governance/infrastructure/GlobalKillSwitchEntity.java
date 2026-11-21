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

/** 全域 kill switch 实体：单例 switch_key=GLOBAL，每次裁决读最新行。 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_global_kill_switch")
public class GlobalKillSwitchEntity extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private String tenantId;

  @Column(name = "switch_key")
  private String switchKey;

  @Column(name = "mode")
  private String mode;

  @Column(name = "operator")
  private String operator;

  @Column(name = "reason")
  private String reason;

  @Column(name = "changed_at")
  private Date changedAt;

  @Column(name = "drill_ref")
  private String drillRef;

  @Version
  @Column(name = "version")
  private Integer version;
}
