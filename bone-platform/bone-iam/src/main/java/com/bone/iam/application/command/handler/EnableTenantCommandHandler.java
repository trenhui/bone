package com.bone.iam.application.command.handler;

import com.bone.core.exception.NotFoundException;
import com.bone.iam.domain.repository.TenantRepository;
import com.bone.iam.domain.tenant.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class EnableTenantCommandHandler {

    private final TenantRepository tenantRepository;

    @Transactional
    public void handle(Long id) {
        Tenant tenant = tenantRepository.findById(id);
        if (tenant == null) {
            throw NotFoundException.of("租户不存在");
        }
        tenant.enable();
        tenantRepository.save(tenant);
    }
}
