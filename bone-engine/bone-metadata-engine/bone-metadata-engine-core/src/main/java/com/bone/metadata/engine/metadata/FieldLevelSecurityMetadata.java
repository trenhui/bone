package com.bone.metadata.engine.metadata;

import com.bone.metadata.engine.model.PermissionMetadata;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 字段级安全元数据模型类 根据设计文档中的数据安全配置需求设计 */
@Getter
@Setter
public class FieldLevelSecurityMetadata implements PermissionMetadata {

  // 字段API名称
  private String fieldApiName;

  // 可读取的角色列表
  private List<String> readableRoles = new ArrayList<>();

  // 可编辑的角色列表
  private List<String> editableRoles = new ArrayList<>();

  // 是否对管理员可见
  private boolean visibleToAdmin = true;

  // 是否可见
  private boolean visible = true;

  // 是否可编辑
  private boolean editable = true;

  // 可读字段列表
  private List<String> readableFields = new ArrayList<>();

  // 可编辑字段列表
  private List<String> editableFields = new ArrayList<>();

  // 字段敏感度级别
  private SensitivityLevel sensitivityLevel = SensitivityLevel.NORMAL;

  // 是否需要审计
  private boolean auditEnabled = false;

  // 审计级别
  private AuditLevel auditLevel = AuditLevel.READ_WRITE;

  // 数据脱敏规则ID
  private String maskingRuleId;

  // 加密算法
  private String encryptionAlgorithm;

  // 敏感数据类型
  private String sensitiveDataType;

  /** 字段敏感度级别枚举 */
  public enum SensitivityLevel {
    /** 普通级别 */
    NORMAL,
    /** 内部级别 */
    INTERNAL,
    /** 机密级别 */
    CONFIDENTIAL,
    /** 高度机密级别 */
    HIGHLY_CONFIDENTIAL
  }

  /** 审计级别枚举 */
  public enum AuditLevel {
    /** 仅审计读操作 */
    READ_ONLY,
    /** 仅审计写操作 */
    WRITE_ONLY,
    /** 审计读写操作 */
    READ_WRITE,
    /** 不审计 */
    NONE
  }

  /** 获取是否可见 */
  public boolean isVisible() {
    return this.visible;
  }

  /** 获取是否可编辑 */
  public boolean isEditable() {
    return this.editable;
  }

  /** 获取可读字段列表 */
  public List<String> getReadableFields() {
    return this.readableFields;
  }

  /** 获取可编辑字段列表 */
  public List<String> getEditableFields() {
    return this.editableFields;
  }

  /** 获取可读角色列表 */
  public List<String> getReadableRoles() {
    return this.readableRoles;
  }

  /** 获取可编辑角色列表 */
  public List<String> getEditableRoles() {
    return this.editableRoles;
  }

  /** 获取安全配置信息 返回当前对象本身作为安全配置 */
  public FieldLevelSecurityMetadata getProfile() {
    return this;
  }

  /** 设置安全配置信息 将传入的安全配置属性复制到当前对象 */
  public void setProfile(FieldLevelSecurityMetadata profile) {
    if (profile != null) {
      // 使用setter方法来设置属性
      this.readableFields = profile.getReadableFields();
      this.editableFields = profile.getEditableFields();
      this.readableRoles = profile.getReadableRoles();
      this.editableRoles = profile.getEditableRoles();
      this.sensitivityLevel = profile.getSensitivityLevel();
      this.auditEnabled = profile.isAuditEnabled();
      this.auditLevel = profile.getAuditLevel();
      this.maskingRuleId = profile.getMaskingRuleId();
      this.encryptionAlgorithm = profile.getEncryptionAlgorithm();
      this.sensitiveDataType = profile.getSensitiveDataType();
    }
  }

  /** 添加可读角色 */
  public void addReadableRole(String role) {
    if (role != null && !readableRoles.contains(role)) {
      readableRoles.add(role);
    }
  }

  /** 添加可编辑角色 */
  public void addEditableRole(String role) {
    if (role != null && !editableRoles.contains(role)) {
      editableRoles.add(role);
    }
  }

  /** 检查角色是否有读权限 */
  public boolean hasReadPermission(String role) {
    return readableRoles.contains(role);
  }

  /** 检查角色是否有编辑权限 */

  /** 获取是否启用审计 */
  public boolean isAuditEnabled() {
    return this.auditEnabled;
  }

  /** 获取审计级别 */
  public AuditLevel getAuditLevel() {
    return this.auditLevel;
  }

  /** 获取数据脱敏规则ID */
  public String getMaskingRuleId() {
    return this.maskingRuleId;
  }

  /** 获取加密算法 */
  public String getEncryptionAlgorithm() {
    return this.encryptionAlgorithm;
  }

  /** 获取敏感数据类型 */
  public String getSensitiveDataType() {
    return this.sensitiveDataType;
  }

  /** 获取字段敏感度级别 */
  public SensitivityLevel getSensitivityLevel() {
    return this.sensitivityLevel;
  }

  public boolean hasEditPermission(String role) {
    return editableRoles.contains(role);
  }

  /** 是否是敏感字段 */
  public boolean isSensitiveField() {
    return sensitivityLevel != SensitivityLevel.NORMAL;
  }

  /** 是否需要数据脱敏 */
  public boolean requiresMasking() {
    return maskingRuleId != null && !maskingRuleId.isEmpty();
  }

  /** 是否需要加密 */
  public boolean requiresEncryption() {
    return encryptionAlgorithm != null && !encryptionAlgorithm.isEmpty();
  }
}
