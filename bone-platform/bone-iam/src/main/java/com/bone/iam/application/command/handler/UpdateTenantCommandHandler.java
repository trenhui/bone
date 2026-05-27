package com.bone.iam.application.command.handler;

import com.bone.core.exception.NotFoundException;
import com.bone.iam.application.command.cmd.UpdateTenantCommand;
import com.bone.iam.domain.repository.TenantRepository;
import com.bone.iam.domain.tenant.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateTenantCommandHandler {

    private final TenantRepository tenantRepository;

    @Transactional
    public void handle(UpdateTenantCommand cmd) {
        Tenant tenant = tenantRepository.findById(cmd.getId());
        if (tenant == null) {
            throw NotFoundException.of("租户不存在");
        }
        tenant.update(cmd.getName(), cmd.getAdminEmail(), cmd.getLevel() != null ? cmd.getLevel() : 0);
        tenantRepository.save(tenant);
    }
}
