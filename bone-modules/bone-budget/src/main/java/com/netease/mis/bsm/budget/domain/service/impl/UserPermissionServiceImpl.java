package com.netease.mis.bsm.budget.domain.service.impl;

import com.bone.core.domain.BaseServiceImpl;
import com.netease.mis.bsm.budget.domain.repository.UserPermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.netease.mis.bsm.budget.domain.model.UserPermission;
import com.netease.mis.bsm.budget.domain.service.UserPermissionService;

/**
 * 预算用户权限 Service 实现类
 *
 * @author 梅山源码
 */
@Service
@Validated
public class UserPermissionServiceImpl extends BaseServiceImpl<UserPermission, Long> implements UserPermissionService {

    private final UserPermissionRepository  userPermissionRepository;

    public UserPermissionServiceImpl(UserPermissionRepository  userPermissionRepository) {
        super(userPermissionRepository);
        this.userPermissionRepository = userPermissionRepository;
    }
}
