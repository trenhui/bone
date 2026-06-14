package com.bone.engine.extension.studio.infrastructure.persistence.entity;

import com.bone.core.annotation.Id;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Metadata SDK 持久化实体：扩展实现 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("exts_extension_impl")
public class ExtStudioExtensionImpl extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private Long tenantId = 0L;

  @Column(name = "extension_point_id")
  private Long extensionPointId;

  @Column(name = "impl_name")
  private String implName;

  @Column(name = "impl_code")
  private String implCode;

  @Column(name = "description")
  private String description;

  @Column(name = "class_name")
  private String className;

  @Column(name = "tenant_code")
  private String tenantCode;

  @Column(name = "biz_code")
  private String bizCode;

  @Column(name = "use_case")
  private String useCase;

  @Column(name = "scenario")
  private String scenario;

  @Column(name = "user_group")
  private String userGroup;

  @Column(name = "priority")
  private Integer priority;

  @Column(name = "config_json")
  private String configJson;

  /** 1=启用 0=禁用 */
  @Column(name = "status")
  private Integer status;

  @Column(name = "is_default")
  private Boolean isDefault;

  @Column(name = "rollout_percent")
  private Integer rolloutPercent;

  @Column(name = "version")
  private Integer version = 0;
}
