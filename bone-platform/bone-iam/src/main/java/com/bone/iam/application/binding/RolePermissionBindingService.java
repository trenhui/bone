package com.bone.iam.application.binding;

import com.bone.iam.domain.gateway.AccountAuthorityCache;
import com.bone.iam.domain.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 维护 {@code iam_role_permission} 绑定：委托域仓储物理删除后重建（表无软删列），并驱逐权限缓存。 */
@Service
@RequiredArgsConstructor
public class RolePermissionBindingService {

  private final RolePermissionRepository rolePermissionRepository;
  private final AccountAuthorityCache accountAuthorityCache;

  @Transactional
  public void replaceBindings(Long roleId, Long[] permissionIds) {
    if (roleId == null) {
      return;
    }
    rolePermissionRepository.replaceBindingsForRole(roleId, permissionIds);
    accountAuthorityCache.evictAccountsForRole(roleId);
  }
}
