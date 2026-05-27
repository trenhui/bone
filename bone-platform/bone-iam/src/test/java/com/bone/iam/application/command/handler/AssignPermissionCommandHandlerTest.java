package com.bone.iam.application.command.handler;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.iam.application.command.cmd.AssignPermissionCommand;
import com.bone.iam.application.service.RolePermissionBindingService;
import com.bone.iam.domain.role.Role;
import com.bone.iam.domain.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssignPermissionCommandHandlerTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RolePermissionBindingService rolePermissionBindingService;

    @InjectMocks
    private AssignPermissionCommandHandler assignPermissionCommandHandler;

    @Test
    void handleAssignsPermissionsWhenRoleExists() {
        AssignPermissionCommand cmd = new AssignPermissionCommand();
        cmd.setRoleId(1L);
        cmd.setPermissionIds(new Long[] {10L, 11L});
        when(roleRepository.findById(1L))
                .thenReturn(Role.create("admin", "SUPER_ADMIN", "test", 1, 0L, null));

        assignPermissionCommandHandler.handle(cmd);

        verify(rolePermissionBindingService).replaceBindings(1L, cmd.getPermissionIds());
    }

    @Test
    void handleRejectsMissingRole() {
        AssignPermissionCommand cmd = new AssignPermissionCommand();
        cmd.setRoleId(99L);
        when(roleRepository.findById(99L)).thenReturn(null);

        assertThrows(RuntimeException.class, () -> assignPermissionCommandHandler.handle(cmd));
    }
}
