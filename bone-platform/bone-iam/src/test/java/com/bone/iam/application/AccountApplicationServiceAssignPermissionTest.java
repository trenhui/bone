package com.bone.iam.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.iam.application.command.cmd.AssignPermissionCommand;
import com.bone.iam.application.service.RolePermissionBindingService;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.domain.gateway.TenantProvider;
import com.bone.iam.domain.repository.RoleRepository;
import com.bone.iam.domain.role.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** AccountApplicationService#assignPermission 单元测试（原 AssignPermissionCommandHandler 逻辑已内联）。 */
@ExtendWith(MockitoExtension.class)
class AccountApplicationServiceAssignPermissionTest {

  @Mock private RoleRepository roleRepository;

  @Mock private RolePermissionBindingService rolePermissionBindingService;

  @Mock private TenantProvider tenantProvider;

  @InjectMocks private AccountApplicationService accountApplicationService;

  @Test
  void handleAssignsPermissionsWhenRoleExists() {
    AssignPermissionCommand cmd = new AssignPermissionCommand();
    cmd.setRoleId(1L);
    cmd.setPermissionIds(new Long[] {10L, 11L});
    when(roleRepository.findById(1L))
        .thenReturn(Role.create("admin", "SUPER_ADMIN", "test", 1, 0L, null));

    accountApplicationService.assignPermission(cmd);

    verify(rolePermissionBindingService).replaceBindings(1L, cmd.getPermissionIds());
  }

  @Test
  void handleRejectsMissingRoleIdAsBadRequest() {
    AssignPermissionCommand cmd = new AssignPermissionCommand();

    assertThatThrownBy(() -> accountApplicationService.assignPermission(cmd))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 400)
        .hasMessageContaining(IamErrorCodes.ROLE_ID_REQUIRED);
  }

  @Test
  void handleRejectsMissingRoleAsNotFound() {
    AssignPermissionCommand cmd = new AssignPermissionCommand();
    cmd.setRoleId(99L);
    when(roleRepository.findById(99L)).thenReturn(null);

    assertThatThrownBy(() -> accountApplicationService.assignPermission(cmd))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 404)
        .hasMessageContaining(IamErrorCodes.ROLE_NOT_FOUND);
  }

  @Test
  void handleRejectsCrossTenantRoleAssignment() {
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(100L);
    AssignPermissionCommand cmd = new AssignPermissionCommand();
    cmd.setRoleId(2L);
    cmd.setPermissionIds(new Long[] {1L});
    when(roleRepository.findById(2L))
        .thenReturn(Role.create("other", "OTHER", "other tenant role", 1, 200L, null));

    assertThatThrownBy(() -> accountApplicationService.assignPermission(cmd))
        .isInstanceOf(BizException.class);
  }
}
