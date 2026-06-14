package com.bone.engine.extension.studio.domain.model;

import com.bone.core.domain.entity.Entity;
import java.time.LocalDateTime;

/** 插件执行日志（对齐 ext_plugin_execution_log）。 */
public class PluginExecutionLog extends Entity<Long> {

  private Long tenantId = 0L;
  private Long pluginId;
  private Long extensionPointId;
  private String executionId;
  private String status;
  private String inputData;
  private String outputData;
  private String errorMessage;
  private Long durationMs;
  private LocalDateTime createdAt = LocalDateTime.now();

  public Long getTenantId() {
    return tenantId;
  }

  public void setTenantId(Long tenantId) {
    this.tenantId = tenantId;
  }

  public Long getPluginId() {
    return pluginId;
  }

  public void setPluginId(Long pluginId) {
    this.pluginId = pluginId;
  }

  public Long getExtensionPointId() {
    return extensionPointId;
  }

  public void setExtensionPointId(Long extensionPointId) {
    this.extensionPointId = extensionPointId;
  }

  public String getExecutionId() {
    return executionId;
  }

  public void setExecutionId(String executionId) {
    this.executionId = executionId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getInputData() {
    return inputData;
  }

  public void setInputData(String inputData) {
    this.inputData = inputData;
  }

  public String getOutputData() {
    return outputData;
  }

  public void setOutputData(String outputData) {
    this.outputData = outputData;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public Long getDurationMs() {
    return durationMs;
  }

  public void setDurationMs(Long durationMs) {
    this.durationMs = durationMs;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
