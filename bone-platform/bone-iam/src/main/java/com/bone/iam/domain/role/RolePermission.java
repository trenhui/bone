package com.bone.iam.domain.role;

import com.bone.core.annotation.Id;
import com.bone.core.domain.entity.Entity;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("iam_role_permission")
public class RolePermission extends Entity<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private Long roleId;
  private Long permissionId;

  public static RolePermission of(Long id, Long roleId, Long permissionId) {
    RolePermission rp = new RolePermission();
    rp.id = id;
    rp.roleId = roleId;
    rp.permissionId = permissionId;
    return rp;
  }
}
