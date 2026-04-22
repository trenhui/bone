package com.bone.iam.application.command.handler;

import com.bone.iam.application.command.cmd.CreateRoleCmd;
import com.bone.iam.domain.model.role.Role;
import com.bone.iam.domain.model.role.vo.RoleName;
import com.bone.iam.domain.repository.RoleRepository;
import com.bone.iam.domain.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateRoleHandler {
    private final RoleRepository roleRepository;
    private final RoleService roleService;

    @Transactional
    public Long handle(CreateRoleCmd cmd) {
        if (roleService.isRoleNameExists(cmd.getName())) {
            throw new RuntimeException("角色名称已存在");
        }

        RoleName roleName = RoleName.of(cmd.getName());
        Role role = Role.create(roleName, cmd.getDescription(), cmd.getTenantId());
        roleRepository.save(role);
        return role.getId().getValue();
    }
}