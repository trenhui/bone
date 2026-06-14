package com.bone.engine.extension.studio.domain.model;

import com.bone.core.domain.entity.Entity;
import java.time.LocalDateTime;

/** 插件版本（对齐 ext_plugin_version 核心字段）。 */
public class PluginVersion extends Entity<Long> {

  private Long pluginId;
  private String version;
  private String filePath;
  private long fileSize;
  private String checksum;
  private boolean active;

  /** 制品部署状态（对齐 {@link com.bone.engine.extension.studio.domain.model.DeploymentStatus}） */
  private String deploymentStatus;

  private String changeLog;
  private LocalDateTime createdAt = LocalDateTime.now();

  public Long getPluginId() {
    return pluginId;
  }

  public void setPluginId(Long pluginId) {
    this.pluginId = pluginId;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  public long getFileSize() {
    return fileSize;
  }

  public void setFileSize(long fileSize) {
    this.fileSize = fileSize;
  }

  public String getChecksum() {
    return checksum;
  }

  public void setChecksum(String checksum) {
    this.checksum = checksum;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public String getDeploymentStatus() {
    return deploymentStatus;
  }

  public void setDeploymentStatus(String deploymentStatus) {
    this.deploymentStatus = deploymentStatus;
  }

  public void setDeploymentStatus(DeploymentStatus status) {
    this.deploymentStatus = status != null ? status.name() : null;
  }

  public String getChangeLog() {
    return changeLog;
  }

  public void setChangeLog(String changeLog) {
    this.changeLog = changeLog;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
