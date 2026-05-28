package com.bone.iam.application.command.handler;

import com.bone.core.exception.NotFoundException;
import com.bone.iam.application.command.cmd.UpdateTenantQuotaCommand;
import com.bone.iam.domain.repository.TenantRepository;
import com.bone.iam.domain.tenant.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateTenantQuotaCommandHandler {

    private final TenantRepository tenantRepository;

    @Transactional
    public void handle(UpdateTenantQuotaCommand cmd) {
        if (cmd == null || cmd.getId() == null) {
            throw new IllegalArgumentException("租户 ID 不能为空");
        }
        if (cmd.getMaxAccounts() != null && cmd.getMaxAccounts() < 0) {
            throw new IllegalArgumentException("maxAccounts 不能为负数");
        }
        if (cmd.getMaxRoles() != null && cmd.getMaxRoles() < 0) {
            throw new IllegalArgumentException("maxRoles 不能为负数");
        }
        Tenant tenant = tenantRepository.findById(cmd.getId());
        if (tenant == null) {
            throw NotFoundException.of("租户不存在");
        }
        tenant.updateQuota(cmd.getMaxAccounts(), cmd.getMaxRoles());
        tenantRepository.save(tenant);
    }
}
