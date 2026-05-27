package com.bone.iam.application.query.handler;

import com.bone.core.exception.NotFoundException;
import com.bone.iam.application.query.dto.TenantDTO;
import com.bone.iam.domain.repository.TenantRepository;
import com.bone.iam.domain.tenant.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TenantDetailQueryHandler {

    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public TenantDTO handle(Long id) {
        Tenant tenant = tenantRepository.findById(id);
        if (tenant == null) {
            throw NotFoundException.of("租户不存在");
        }
        TenantDTO dto = new TenantDTO();
        dto.setId(tenant.getId());
        dto.setName(tenant.getName());
        dto.setCode(tenant.getCode());
        dto.setLevel(tenant.getLevel());
        dto.setStatus(tenant.getStatus());
        dto.setAdminEmail(tenant.getAdminEmail());
        return dto;
    }
}
