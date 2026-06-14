package com.bone.core.security.enums;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public enum UserType {
  // ================ 系统核心角色 ================
  ROOT_ADMIN("根管理员", RoleDomain.SYSTEM, PermissionLevel.ALL, "系统最高权限管理员", true, true, true),
  SYSTEM_ADMIN("系统管理员", RoleDomain.SYSTEM, PermissionLevel.ADMIN, "负责系统全局配置", true, true, true),
  AUDITOR("审计员", RoleDomain.SYSTEM, PermissionLevel.READ_ONLY, "系统操作审计监督", true, false, true),
  SYSTEM_BOT("系统机器人", RoleDomain.SYSTEM, PermissionLevel.SERVICE, "自动执行系统任务", false, false, false),

  // ================ 组织管理角色 ================
  ORG_OWNER("组织所有者", RoleDomain.ORGANIZATION, PermissionLevel.ADMIN, "组织最高管理者", true, true, true),
  ORG_ADMIN("组织管理员", RoleDomain.ORGANIZATION, PermissionLevel.ADMIN, "管理组织资源", true, true, true),
  ORG_MANAGER(
      "部门经理", RoleDomain.ORGANIZATION, PermissionLevel.STANDARD, "部门级管理权限", false, true, false),
  ORG_MEMBER(
      "组织成员", RoleDomain.ORGANIZATION, PermissionLevel.STANDARD, "普通成员权限", false, false, false),
  ORG_VIP(
      "高级成员", RoleDomain.ORGANIZATION, PermissionLevel.PREMIUM, "付费高级功能权限", false, false, false),
  ORG_GUEST(
      "访客成员", RoleDomain.ORGANIZATION, PermissionLevel.LIMITED, "受限访问权限", false, false, false),

  // ================ 外部协作角色 ================
  PARTNER_ADMIN(
      "合作伙伴管理员", RoleDomain.EXTERNAL, PermissionLevel.LIMITED, "第三方协作管理", true, true, true),
  EXTERNAL_USER(
      "外部用户", RoleDomain.EXTERNAL, PermissionLevel.LIMITED, "外部协作人员", false, false, false),
  API_CLIENT("API客户端", RoleDomain.EXTERNAL, PermissionLevel.SERVICE, "系统集成账户", false, false, false),

  // ================ 特殊场景角色 ================
  GUEST("访客", RoleDomain.PUBLIC, PermissionLevel.READ_ONLY, "未认证临时用户", false, false, false),
  ANONYMOUS("匿名用户", RoleDomain.PUBLIC, PermissionLevel.NONE, "完全匿名访问", false, false, false);

  // ================ 核心枚举定义 ================
  public enum RoleDomain {
    SYSTEM("系统级", 100), // 跨组织核心管理
    ORGANIZATION("组织级", 80), // 租户/组织内部角色
    EXTERNAL("外部级", 60), // 第三方协作
    PUBLIC("公开级", 40); // 无需认证

    private final String description;
    private final int hierarchyLevel; // 角色层级值

    RoleDomain(String description, int hierarchyLevel) {
      this.description = description;
      this.hierarchyLevel = hierarchyLevel;
    }

    public boolean canManage(RoleDomain target) {
      return this.hierarchyLevel > target.hierarchyLevel;
    }
  }

  public enum PermissionLevel {
    ALL("全部权限", 100),
    ADMIN("管理权限", 90),
    PREMIUM("高级权限", 80),
    STANDARD("标准权限", 70),
    LIMITED("受限权限", 50),
    READ_ONLY("只读权限", 30),
    SERVICE("服务权限", 95), // 高于ADMIN但低于ALL
    NONE("无权限", 0);

    private final String description;
    private final int levelValue;

    PermissionLevel(String description, int levelValue) {
      this.description = description;
      this.levelValue = levelValue;
    }

    public boolean includes(PermissionLevel required) {
      return this.levelValue >= required.levelValue;
    }
  }

  // ================ 角色属性 ================
  private final String displayName;
  private final RoleDomain domain;
  private final PermissionLevel basePermission;
  private final String description;
  private final boolean allowMultiFactorAuth; // 是否强制MFA
  private final boolean allowPermissionOverride; // 是否允许权限提升
  private final boolean requireAuditLog; // 是否需要审计日志

  // ================ 权限映射 ================
  static final Map<UserType, Set<String>> PERMISSION_MAP = new ConcurrentHashMap<>();

  static {
    // 初始化核心权限
    PERMISSION_MAP.put(ROOT_ADMIN, Set.of("*"));
    PERMISSION_MAP.put(SYSTEM_ADMIN, Set.of("system:*", "audit:read"));
    PERMISSION_MAP.put(AUDITOR, Set.of("audit:*", "report:generate"));
    PERMISSION_MAP.put(ORG_OWNER, Set.of("org:*", "billing:manage"));
    PERMISSION_MAP.put(ORG_ADMIN, Set.of("user:manage", "data:*", "config:update"));

    // 注册权限扩展点
    PermissionExtension.registerDynamicPermissions();
  }

  // ================ 构造函数 ================
  UserType(
      String displayName,
      RoleDomain domain,
      PermissionLevel basePermission,
      String description,
      boolean allowMFA,
      boolean allowOverride,
      boolean requireAudit) {
    this.displayName = displayName;
    this.domain = domain;
    this.basePermission = basePermission;
    this.description = description;
    this.allowMultiFactorAuth = allowMFA;
    this.allowPermissionOverride = allowOverride;
    this.requireAuditLog = requireAudit;
  }

  // ================ 核心方法 ================
  /** 检查是否拥有指定权限级别 */
  public boolean hasPermissionLevel(PermissionLevel required) {
    return this.basePermission.includes(required);
  }

  /** 检查具体权限码（支持通配符） */
  public boolean hasPermission(String permissionCode) {
    // 系统超级权限
    if (this == ROOT_ADMIN) return true;

    Set<String> permissions = PERMISSION_MAP.getOrDefault(this, Collections.emptySet());

    // 通配符匹配
    return permissions.stream()
        .anyMatch(
            p ->
                p.equals("*")
                    || p.equals(permissionCode)
                    || (p.endsWith(":*") && permissionCode.startsWith(p.replace(":*", ":"))));
  }

  /** 是否可管理目标角色 */
  public boolean canManage(UserType target) {
    // 不允许自我管理
    if (this == target) return false;

    // 系统角色可管理所有非系统角色
    if (this.domain == RoleDomain.SYSTEM && target.domain != RoleDomain.SYSTEM) {
      return true;
    }

    // 同域内层级管理
    return this.domain == target.domain
        && this.basePermission.levelValue > target.basePermission.levelValue;
  }

  // ================ 扩展方法 ================
  /** 获取动态权限集合 */
  public Set<String> getEffectivePermissions() {
    return Collections.unmodifiableSet(PERMISSION_MAP.getOrDefault(this, Collections.emptySet()));
  }

  /** 添加临时权限（需权限提升许可） */
  public void addTemporaryPermission(String permission) {
    if (this.allowPermissionOverride) {
      PERMISSION_MAP.computeIfAbsent(this, k -> new HashSet<>()).add(permission);
    }
  }

  // ================ 属性判断 ================
  public boolean isSystemRole() {
    return domain == RoleDomain.SYSTEM;
  }

  public boolean isSuperAdmin() {
    return this == ROOT_ADMIN || this == SYSTEM_ADMIN;
  }

  public boolean isAuditRequired() {
    return requireAuditLog;
  }

  // ================ 属性访问器 ================
  public String getDisplayName() {
    return displayName;
  }

  public RoleDomain getDomain() {
    return domain;
  }

  public PermissionLevel getBasePermission() {
    return basePermission;
  }

  @Override
  public String toString() {
    return String.format("%s [%s]", displayName, domain.description);
  }
}

// ================ 权限扩展机制 ================
class PermissionExtension {
  /** 从数据库/配置文件加载动态权限 */
  public static void registerDynamicPermissions() {
    // 示例：从外部源加载权限配置
    Map<UserType, Set<String>> dynamicPermissions = loadPermissionsFromSource();

    dynamicPermissions.forEach(
        (type, perms) ->
            perms.forEach(
                perm ->
                    UserType.PERMISSION_MAP.computeIfAbsent(type, k -> new HashSet<>()).add(perm)));
  }

  private static Map<UserType, Set<String>> loadPermissionsFromSource() {
    // 实际实现中从数据库或配置文件读取
    return Map.of(
        UserType.ORG_VIP, Set.of("premium:access", "data:export"),
        UserType.PARTNER_ADMIN, Set.of("partner:manage", "data:limited"));
  }
}
