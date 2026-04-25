package com.bone.iam.domain.service;

import com.bone.iam.domain.role.Role;
import com.bone.iam.domain.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepository roleRepository;

    public Role getRoleById(Long id) {
        return roleRepository.findById(id);
    }
}
