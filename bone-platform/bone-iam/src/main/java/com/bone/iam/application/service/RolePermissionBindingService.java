package com.bone.iam.application.service;

import com.bone.core.util.DistributedIdGenerator;
import com.bone.iam.domain.repository.RolePermissionRepository;
import com.bone.iam.domain.role.RolePermission;
import com.bone.iam.infrastructure.security.AuthorityCacheEvictionService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 维护 {@code iam_role_permission} 绑定（物理删除后重建，表无软删列）。 */
@Service
@RequiredArgsConstructor
public class RolePermissionBindingService {

    private final RolePermissionRepository rolePermissionRepository;
    private final AuthorityCacheEvictionService authorityCacheEvictionService;

    @Transactional
    public void replaceBindings(Long roleId, Long[] permissionIds) {
        if (roleId == null) {
            return;
        }
        rolePermissionRepository
                .getSqlExecutor()
                .delete("DELETE FROM iam_role_permission WHERE role_id = ?", roleId);

        if (permissionIds == null || permissionIds.length == 0) {
            authorityCacheEvictionService.evictAccountsForRole(roleId);
            return;
        }
        List<RolePermission> links = Arrays.stream(permissionIds)
                .filter(Objects::nonNull)
                .distinct()
                .map(permissionId -> RolePermission.of(
                        DistributedIdGenerator.generateLongId(), roleId, permissionId))
                .collect(Collectors.toCollection(ArrayList::new));
        if (!links.isEmpty()) {
            rolePermissionRepository.batchInsert(links);
        }
        authorityCacheEvictionService.evictAccountsForRole(roleId);
    }
}
