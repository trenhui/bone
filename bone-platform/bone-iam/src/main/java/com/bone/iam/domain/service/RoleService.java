package com.bone.iam.domain.service;

import com.bone.iam.domain.model.role.Role;
import com.bone.iam.domain.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepository roleRepository;

    public boolean isRoleNameExists(String name) {
        return roleRepository.existsByName(name);
    }

    public Role getRoleByName(String name) {
        return roleRepository.findByName(name).orElse(null);
    }
}