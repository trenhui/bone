package com.bone.metadata.catalog.domain.model.iam;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 只读引用实体：指向 IAM 上下文独占的 {@code bone_app_permission} 表（G1②）。
 *
 * <p>领域边界：应用权限（用户在应用内的角色）聚合根归属 IAM（bone-iam）。metadata 侧仅持只读视图， 用于「同租户跨应用建模越权」校验（2a §G1②）：
 * 建模者须持有目标应用 {@code ADMIN}/{@code DEVELOPER} 角色。不含任何写操作。
 */
@Getter
@NoArgsConstructor
@Table("bone_app_permission")
public class IamAppRoleRef extends AbstractEntity<Long> {

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "app_id", nullable = false)
  private Long appId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  /** 角色的大写存储值：ADMIN / DEVELOPER / VIEWER（与 IAM {@code AppRole.name()} 同口径）。 */
  @Column(name = "role", nullable = false)
  private String role;
}
