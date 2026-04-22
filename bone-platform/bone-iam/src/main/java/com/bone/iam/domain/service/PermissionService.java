package com.bone.iam.domain.service;

import com.bone.iam.domain.model.permission.Permission;
import com.bone.iam.domain.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PermissionService {
    private final PermissionRepository permissionRepository;

    public boolean isPermissionCodeExists(String code) {
        return permissionRepository.existsByCode(code);
    }

    public Permission getPermissionByCode(String code) {
        return permissionRepository.findByCode(code);
    }
}