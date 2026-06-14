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

/** Metadata SDK 持久化实体：插件执行日志 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("exts_plugin_execution_log")
public class ExtStudioPluginExecutionLog extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private Long tenantId = 0L;

  @Column(name = "plugin_id")
  private Long pluginId;

  @Column(name = "extension_point_id")
  private Long extensionPointId;

  @Column(name = "execution_id")
  private String executionId;

  @Column(name = "status")
  private String status;

  @Column(name = "input_data")
  private String inputData;

  @Column(name = "output_data")
  private String outputData;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "duration_ms")
  private Long durationMs;
}
