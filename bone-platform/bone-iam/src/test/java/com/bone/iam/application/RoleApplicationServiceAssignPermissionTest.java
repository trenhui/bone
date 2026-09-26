package com.bone.iam.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.iam.application.command.AssignPermissionCommand;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.domain.gateway.AccountAuthorityCache;
import com.bone.iam.domain.gateway.TenantProvider;
import com.bone.iam.domain.model.permission.Permission;
import com.bone.iam.domain.model.permission.valueobject.PermissionType;
import com.bone.iam.domain.model.role.Role;
import com.bone.iam.domain.repository.PermissionRepository;
import com.bone.iam.domain.repository.RolePermissionRepository;
import com.bone.iam.domain.repository.RoleRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** RoleApplicationService#assignPermission 单元测试（原 AssignPermissionCommandHandler 逻辑已内联）。 */
@ExtendWith(MockitoExtension.class)
class RoleApplicationServiceAssignPermissionTest {

  @Mock private RoleRepository roleRepository;
  @Mock private RolePermissionRepository rolePermissionRepository;
  @Mock private PermissionRepository permissionRepository;
  @Mock private AccountAuthorityCache accountAuthorityCache;
  @Mock private TenantProvider tenantProvider;

  @InjectMocks private RoleApplicationService roleApplicationService;

  @Test
  void handleAssignsPermissionsWhenRoleExists() {
    AssignPermissionCommand cmd = new AssignPermissionCommand();
    cmd.setRoleId(1L);
    cmd.setPermissionIds(new Long[] {10L, 11L});
    when(roleRepository.findById(1L))
        .thenReturn(Role.create("admin", "SUPER_ADMIN", "test", 1, 0L, null));

    roleApplicationService.assignPermission(cmd);

    verify(rolePermissionRepository).replaceBindingsForRole(1L, cmd.getPermissionIds());
    verify(accountAuthorityCache).evictAccountsForRole(1L);
  }

  @Test
  void handleRejectsMissingRoleIdAsBadRequest() {
    AssignPermissionCommand cmd = new AssignPermissionCommand();

    assertThatThrownBy(() -> roleApplicationService.assignPermission(cmd))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 400)
        .hasMessageContaining(IamErrorCodes.ROLE_ID_REQUIRED);
  }

  @Test
  void handleRejectsMissingRoleAsNotFound() {
    AssignPermissionCommand cmd = new AssignPermissionCommand();
    cmd.setRoleId(99L);
    when(roleRepository.findById(99L)).thenReturn(null);

    assertThatThrownBy(() -> roleApplicationService.assignPermission(cmd))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 404)
        .hasMessageContaining(IamErrorCodes.ROLE_NOT_FOUND);
  }

  /** 租户管理员不得把平台域权限码（资源路径 tenants/permissions/sessions）绑进本租户角色。 */
  @Test
  void handleRejectsPlatformScopedPermissionForTenantAdmin() {
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(100L);
    AssignPermissionCommand cmd = new AssignPermissionCommand();
    cmd.setRoleId(3L);
    cmd.setPermissionIds(new Long[] {20L});
    when(roleRepository.findById(3L))
        .thenReturn(Role.create("ops", "OPS", "tenant role", 1, 100L, null));
    // 接口 default 方法须用 doReturn 打桩，避免真实执行 default 体（详见项目约定）
    doReturn(List.of(platformPermission("iam:tenants:write", "tenants")))
        .when(permissionRepository)
        .findByIds(anyList());

    assertThatThrownBy(() -> roleApplicationService.assignPermission(cmd))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 403)
        .hasMessageContaining(IamErrorCodes.PERMISSION_PLATFORM_ONLY);
  }

  /** 平台管理员（tenantId=0）不受平台域限制——代租户授权是其职责。 */
  @Test
  void handleAllowsPlatformScopedPermissionForPlatformAdmin() {
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(0L);
    AssignPermissionCommand cmd = new AssignPermissionCommand();
    cmd.setRoleId(4L);
    cmd.setPermissionIds(new Long[] {20L});
    when(roleRepository.findById(4L))
        .thenReturn(Role.create("sa", "SUPER", "platform role", 1, 0L, null));

    assertThatCode(() -> roleApplicationService.assignPermission(cmd)).doesNotThrowAnyException();
    verify(rolePermissionRepository).replaceBindingsForRole(4L, cmd.getPermissionIds());
  }

  /** 即使调用方是平台管理员，平台域码也不得进入**租户角色**——判定看角色归属而非调用方身份（否则平台管理员误操作即等于把平台能力下发到租户）。 */
  @Test
  void handleRejectsPlatformScopedPermissionOnTenantRoleEvenForPlatformAdmin() {
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(0L);
    AssignPermissionCommand cmd = new AssignPermissionCommand();
    cmd.setRoleId(5L);
    cmd.setPermissionIds(new Long[] {20L});
    when(roleRepository.findById(5L))
        .thenReturn(Role.create("ops", "OPS", "tenant role", 1, 100L, null));
    doReturn(List.of(platformPermission("iam:tenants:write", "tenants")))
        .when(permissionRepository)
        .findByIds(anyList());

    assertThatThrownBy(() -> roleApplicationService.assignPermission(cmd))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 403)
        .hasMessageContaining(IamErrorCodes.PERMISSION_PLATFORM_ONLY);
  }

  private static Permission platformPermission(String code, String resourcePath) {
    return Permission.create(
        code, "平台域权限", "test", "iam", resourcePath, "write", null, PermissionType.OPERATION, 300);
  }

  @Test
  void handleRejectsCrossTenantRoleAssignment() {
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(100L);
    AssignPermissionCommand cmd = new AssignPermissionCommand();
    cmd.setRoleId(2L);
    cmd.setPermissionIds(new Long[] {1L});
    when(roleRepository.findById(2L))
        .thenReturn(Role.create("other", "OTHER", "other tenant role", 1, 200L, null));

    assertThatThrownBy(() -> roleApplicationService.assignPermission(cmd))
        .isInstanceOf(BizException.class);
  }
}
