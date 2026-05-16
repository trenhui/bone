package com.bone.metadata.engine.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Entity permission metadata model class Used to define entity access permission control policies
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class EntityPermissionMetadata {
  // Basic permission configuration
  @Builder.Default private boolean publicRead = false;
  @Builder.Default private boolean publicWrite = false;
  @Builder.Default private boolean requireAuthentication = true;

  // Role permission mapping
  @Builder.Default private Map<String, PermissionLevel> rolePermissions = new HashMap<>();

  // Field-level permission configuration
  @Builder.Default private Map<String, FieldPermissionMetadata> fieldPermissions = new HashMap<>();

  // Conditional permission rules
  @Builder.Default private List<ConditionalPermissionRule> conditionalRules = new ArrayList<>();

  // Sharing rules
  @Builder.Default private List<SharingRule> sharingRules = new ArrayList<>();

  // Audit configuration
  @Builder.Default private boolean auditAllOperations = true;
  @Builder.Default private boolean auditFieldChanges = true;

  // Data isolation level
  @Builder.Default private DataIsolationLevel dataIsolationLevel = DataIsolationLevel.TENANT;

  /** Permission level enumeration */
  public enum PermissionLevel {
    NONE, // No permission
    READ, // Read-only permission
    CREATE, // Create permission
    UPDATE, // Update permission
    DELETE, // Delete permission
    MANAGE // Management permission (all)
  }

  /** Data isolation level enumeration */
  public enum DataIsolationLevel {
    GLOBAL, // Globally shared
    TENANT, // Tenant isolated
    USER, // User isolated
    CUSTOM // Custom isolation
  }

  /** Field permission metadata inner class */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FieldPermissionMetadata {
    private String fieldName;
    @Builder.Default private boolean readable = true;
    @Builder.Default private boolean writable = true;
    @Builder.Default private boolean required = false;
    private String accessFilterExpression;
  }

  /** Conditional permission rule inner class */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ConditionalPermissionRule {
    private String ruleName;
    private String condition;
    private PermissionLevel permissionLevel;
    private List<String> allowedOperations;
  }

  /** Sharing rule inner class */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SharingRule {
    private String ruleName;
    private String sourceRole;
    private String targetRole;
    private PermissionLevel permissionLevel;
    private String filterCondition;
  }

  /** Adds role permission */
  public void addRolePermission(String roleName, PermissionLevel permissionLevel) {
    if (rolePermissions == null) {
      rolePermissions = new HashMap<>();
    }
    rolePermissions.put(roleName, permissionLevel);
  }

  /** Gets role permission */
  public PermissionLevel getRolePermission(String roleName) {
    if (rolePermissions == null) {
      return null;
    }
    return rolePermissions.get(roleName);
  }

  /** Adds field permission */
  public void addFieldPermission(String fieldName, FieldPermissionMetadata permission) {
    if (fieldPermissions == null) {
      fieldPermissions = new HashMap<>();
    }
    fieldPermissions.put(fieldName, permission);
  }

  /** Gets field permission */
  public FieldPermissionMetadata getFieldPermission(String fieldName) {
    if (fieldPermissions == null) {
      return null;
    }
    return fieldPermissions.get(fieldName);
  }
}
