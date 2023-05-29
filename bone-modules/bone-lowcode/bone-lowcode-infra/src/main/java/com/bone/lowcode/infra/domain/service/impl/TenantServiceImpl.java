package com.bone.lowcode.infra.domain.service.impl;

import com.bone.core.domain.BaseServiceImpl;
import com.bone.lowcode.infra.domain.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.bone.lowcode.infra.domain.model.Tenant;
import com.bone.lowcode.infra.domain.service.TenantService;

/**
 * 租户 Service 实现类
 *
 * @author 梅山源码
 */
@Service
@Validated
public class TenantServiceImpl extends BaseServiceImpl<Tenant, Long> implements TenantService {

    private final TenantRepository  tenantRepository;

    public TenantServiceImpl(TenantRepository  tenantRepository) {
        super(tenantRepository);
        this.tenantRepository = tenantRepository;
    }
}
