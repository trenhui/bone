package com.bone.iam.domain.model.permission;

import com.bone.core.annotation.Deleted;
import com.bone.core.annotation.Id;
import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.iam.domain.model.permission.valueobject.PermissionType;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("iam_permission")
public class Permission extends AggregateRoot<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  // 逻辑删除标记：聚合根基类（TenantAggregateRoot / AggregateRoot）不自带 deleted 字段；
  // 不声明则 Repository#deleteById 发出 DELETE FROM，行永久消失且不可审计、不可恢复。
  // 补 @Deleted 后 SDK 走 UPDATE deleted=1 软删；配套 DDL（0020_soft_delete_unique_index.sql）
  // 已把本表唯一索引纳入 deleted 列，避免「软删后同值无法重建」。
  @Deleted private Boolean deleted = false;

  private String code;
  private String name;
  private String description;
  private String resourceType;
  private String resourcePath;
  private String action;
  private Long parentId;
  private PermissionType type;
  private int sortOrder;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static Permission create(
      String code,
      String name,
      String description,
      String resourceType,
      String resourcePath,
      String action,
      Long parentId,
      PermissionType type,
      int sortOrder) {
    Permission permission = new Permission();
    permission.code = code;
    permission.name = name;
    permission.description = description;
    permission.resourceType = resourceType;
    permission.resourcePath = resourcePath;
    permission.action = action;
    permission.parentId = parentId;
    permission.type = type;
    permission.sortOrder = sortOrder;
    permission.createdAt = LocalDateTime.now();
    permission.updatedAt = LocalDateTime.now();
    return permission;
  }

  /** 平台域资源路径集合；新增平台域资源时须同步此处（[Target] 收敛为 {@code iam_permission.scope} 列）。 */
  private static final Set<String> PLATFORM_RESOURCE_PATHS =
      Set.of("tenants", "permissions", "sessions");

  /**
   * 是否平台域权限（仅平台管理员可授予给角色）。
   *
   * <p>平台域 = {@code iam_permission.resource_path} 落在「租户 / 权限目录 / 全局会话」三处：它们作用于平台级资源，一旦被租户管理员
   * 绑进本租户角色，其成员即可凭该码调用 {@code /tenants}、{@code /permissions} 等平台域接口（这些接口只做 {@code hasAuthority}
   * 校验、不校验 tenantId=0）——即「租户自授平台能力」。因此绑定侧必须拦截，不能只依赖权限码目录的共享性 （租户不可造码 ≠ 不可绑码）。
   *
   * @see com.bone.iam.application.RoleApplicationService#assignPermission
   */
  public boolean isPlatformScoped() {
    return resourcePath != null && PLATFORM_RESOURCE_PATHS.contains(resourcePath);
  }

  public void update(
      String name,
      String description,
      String resourceType,
      String resourcePath,
      String action,
      Long parentId,
      PermissionType type,
      int sortOrder) {
    if (name != null) {
      this.name = name;
    }
    this.description = description;
    if (resourceType != null) {
      this.resourceType = resourceType;
    }
    if (resourcePath != null) {
      this.resourcePath = resourcePath;
    }
    if (action != null) {
      this.action = action;
    }
    this.parentId = parentId;
    if (type != null) {
      this.type = type;
    }
    this.sortOrder = sortOrder;
    this.updatedAt = LocalDateTime.now();
  }
}
