package com.bone.iam.application.service;

import com.bone.iam.domain.repository.RoleRepository;
import com.bone.iam.domain.role.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service("applicationRoleService")
@RequiredArgsConstructor
public class RoleService {
  private final RoleRepository roleRepository;

  public Role getRoleById(Long id) {
    return roleRepository.findById(id);
  }
}
