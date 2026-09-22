package com.bone.iam.domain.model.app;

import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.iam.domain.model.app.vo.AppRole;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 应用权限聚合根：某个用户在某个应用内被授予的角色。
 *
 * <p>唯一键为 (app_id, user_id)——同一用户在同一应用内只保留一条角色绑定；再次授予即改角色。 对应设计：{@code doc/design/modules/10.
 * 应用与模块管理详细设计方案.md} §2.4。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("bone_app_permission")
public class AppPermission extends TenantAggregateRoot<Long> {

  private Long appId;
  private Long userId;
  private AppRole role;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static AppPermission create(Long appId, Long userId, AppRole role, Long tenantId) {
    if (appId == null) {
      throw new DomainException("应用ID不能为空");
    }
    if (userId == null) {
      throw new DomainException("用户ID不能为空");
    }
    if (role == null) {
      throw new DomainException("应用角色不能为空");
    }
    AppPermission permission = new AppPermission();
    permission.appId = appId;
    permission.userId = userId;
    permission.role = role;
    permission.setTenantId(tenantId);
    permission.createdAt = LocalDateTime.now();
    permission.updatedAt = LocalDateTime.now();
    return permission;
  }

  /** 变更角色（幂等授予场景：已存在绑定时更新其角色）。 */
  public void changeRole(AppRole newRole) {
    if (newRole == null) {
      throw new DomainException("应用角色不能为空");
    }
    this.role = newRole;
    this.updatedAt = LocalDateTime.now();
  }
}
