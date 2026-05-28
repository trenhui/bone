package com.bone.iam.application.command.handler;

import com.bone.core.exception.BizException;
import com.bone.core.exception.NotFoundException;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.domain.gateway.TenantDeletionGateway;
import com.bone.iam.domain.repository.TenantRepository;
import com.bone.iam.domain.tenant.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DeleteTenantCommandHandler {

    private static final long PLATFORM_TENANT_ID = 0L;

    private final TenantRepository tenantRepository;
    private final TenantDeletionGateway tenantDeletionGateway;

    @Transactional
    public void handle(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("租户 ID 不能为空");
        }
        if (id == PLATFORM_TENANT_ID) {
            throw BizException.of(400, IamErrorCodes.TENANT_DELETE_FORBIDDEN + ": 禁止删除平台租户");
        }
        Tenant tenant = tenantRepository.findById(id);
        if (tenant == null) {
            throw NotFoundException.of("租户不存在");
        }
        tenantDeletionGateway.purgeTenantData(id);
        tenantRepository.deleteById(id);
    }
}
