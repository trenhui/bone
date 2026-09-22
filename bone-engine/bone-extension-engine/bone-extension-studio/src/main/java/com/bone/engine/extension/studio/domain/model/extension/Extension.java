package com.bone.engine.extension.studio.domain.model.extension;

import com.bone.core.domain.entity.Entity;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 扩展实现领域模型 用于存储扩展实现的元数据信息 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Extension extends Entity<Long> {

  private Long extPointId;
  private String name;
  private String description;
  private String className;
  private String tenantCode = "DEFAULT";
  private String bizCode = "*";
  private String useCase = "*";
  private String scenario = "*";
  private String userGroup = "*";
  private Integer priority = 0;
  private String config;
  private boolean enabled = true;
  private Integer version = 1;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  // 工厂方法
  public static Extension create(
      Long extPointId, String name, String description, String className) {
    Extension extension = new Extension();
    extension.extPointId = extPointId;
    extension.name = name;
    extension.description = description;
    extension.className = className;
    extension.createdAt = LocalDateTime.now();
    extension.updatedAt = LocalDateTime.now();
    return extension;
  }

  // 业务方法
  public void update(String name, String description) {
    this.name = name;
    this.description = description;
    this.updatedAt = LocalDateTime.now();
  }

  public void enable() {
    this.enabled = true;
    this.updatedAt = LocalDateTime.now();
  }

  public void disable() {
    this.enabled = false;
    this.updatedAt = LocalDateTime.now();
  }

  public void setPriority(int priority) {
    this.priority = priority;
    this.updatedAt = LocalDateTime.now();
  }

  public void setConfig(String config) {
    this.config = config;
    this.updatedAt = LocalDateTime.now();
  }

  public void setExtPointId(Long extPointId) {
    this.extPointId = extPointId;
    this.updatedAt = LocalDateTime.now();
  }

  public void setName(String name) {
    this.name = name;
    this.updatedAt = LocalDateTime.now();
  }

  public void setDescription(String description) {
    this.description = description;
    this.updatedAt = LocalDateTime.now();
  }

  public void setClassName(String className) {
    this.className = className;
    this.updatedAt = LocalDateTime.now();
  }

  public void setTenantCode(String tenantCode) {
    this.tenantCode = tenantCode;
    this.updatedAt = LocalDateTime.now();
  }

  public void setBizCode(String bizCode) {
    this.bizCode = bizCode;
    this.updatedAt = LocalDateTime.now();
  }

  public void setScenario(String scenario) {
    this.scenario = scenario;
    this.updatedAt = LocalDateTime.now();
  }

  public void setUseCase(String useCase) {
    this.useCase = useCase;
    this.updatedAt = LocalDateTime.now();
  }

  public void setUserGroup(String userGroup) {
    this.userGroup = userGroup;
    this.updatedAt = LocalDateTime.now();
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
    this.updatedAt = LocalDateTime.now();
  }

  // Getter方法
  public Long getExtPointId() {
    return extPointId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getClassName() {
    return className;
  }

  public String getTenantCode() {
    return tenantCode;
  }

  public String getBizCode() {
    return bizCode;
  }

  public String getUseCase() {
    return useCase;
  }

  public String getScenario() {
    return scenario;
  }

  public String getUserGroup() {
    return userGroup;
  }

  public Integer getPriority() {
    return priority;
  }

  public String getConfig() {
    return config;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  /** 兼容前端传入字符串版本号（如 "1.0"），取整数部分 */
  @JsonSetter("version")
  public void setVersionFromJson(Object versionValue) {
    if (versionValue == null) {
      this.version = null;
    } else if (versionValue instanceof Integer i) {
      this.version = i;
    } else if (versionValue instanceof Number n) {
      this.version = n.intValue();
    } else {
      String s = versionValue.toString().trim();
      try {
        this.version = Integer.parseInt(s.contains(".") ? s.substring(0, s.indexOf(".")) : s);
      } catch (NumberFormatException e) {
        this.version = 1;
      }
    }
  }
}
