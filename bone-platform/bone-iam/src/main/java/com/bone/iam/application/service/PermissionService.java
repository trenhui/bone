package com.bone.iam.application.service;

import com.bone.iam.domain.permission.Permission;
import com.bone.iam.domain.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service("applicationPermissionService")
@RequiredArgsConstructor
public class PermissionService {
  private final PermissionRepository permissionRepository;

  public Permission getPermissionById(Long id) {
    return permissionRepository.findById(id);
  }
}
