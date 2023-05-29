package com.bone.lowcode.infra.application;

import com.bone.lowcode.infra.domain.model.Tenant;
import com.bone.lowcode.infra.domain.service.TenantService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * 租户 ApplicationService
 *
 * @author 梅山源码
 */
public class TenantApplicationService {

    @Resource
    private TenantService tenantService;

    /**
     * 创建租户
     *
     * @param tenant 租户
     * @return Id
     */
    public Long create(@Valid Tenant tenant) {
        return tenantService.create(tenant).getId();
    }
}
