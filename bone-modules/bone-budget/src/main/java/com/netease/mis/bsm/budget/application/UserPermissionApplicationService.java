package com.netease.mis.bsm.budget.application;

import com.netease.mis.bsm.budget.domain.model.UserPermission;
import com.netease.mis.bsm.budget.domain.service.UserPermissionService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * 预算用户权限 ApplicationService
 *
 * @author 梅山源码
 */
public class UserPermissionApplicationService {

    @Resource
    private UserPermissionService userPermissionService;

    /**
     * 创建预算用户权限
     *
     * @param userPermission 预算用户权限
     * @return Id
     */
    public Long create(@Valid UserPermission userPermission) {
        return userPermissionService.create(userPermission).getId();
    }
}
