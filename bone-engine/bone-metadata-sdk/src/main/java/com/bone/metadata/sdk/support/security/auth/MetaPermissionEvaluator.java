package com.bone.metadata.sdk.support.security.auth;


import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.support.security.service.MetaPermissionService;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;

import java.io.Serializable;

public class MetaPermissionEvaluator implements PermissionEvaluator {

    private final MetaPermissionService permissionService;

    public MetaPermissionEvaluator(MetaPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    public boolean hasPermission(Authentication auth, Object targetDomainObject, Object permission) {
        if (auth == null || targetDomainObject == null || !(permission instanceof String)) {
            return false;
        }
        //todo
        String perm = (String) permission;
        if (targetDomainObject instanceof TableMetadata) {
            TableMetadata meta = (TableMetadata) targetDomainObject;
            return permissionService.hasPermission(auth.getName(), meta.getName(), perm);
        }
        return false;
    }

    @Override
    public boolean hasPermission(Authentication auth, Serializable targetId, String targetType, Object permission) {
        if (auth == null || targetId == null || !(permission instanceof String)) {
            return false;
        }
        return permissionService.hasPermission(auth.getName(), (Long) targetId, (String) permission);
    }
}
