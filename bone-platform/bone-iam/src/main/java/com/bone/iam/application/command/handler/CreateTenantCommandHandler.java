package com.bone.iam.application.command.handler;

import com.bone.core.exception.BizException;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.iam.application.command.cmd.CreateTenantCommand;
import com.bone.iam.domain.repository.TenantRepository;
import com.bone.iam.domain.tenant.Tenant;
import com.bone.metadata.sdk.query.criteria.Criteria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateTenantCommandHandler {

    private final TenantRepository tenantRepository;

    @Transactional
    public Long handle(CreateTenantCommand cmd) {
        long existing =
                tenantRepository.countByCriteria(
                        Criteria.<Tenant>create().eq("code", cmd.getCode()));
        if (existing > 0) {
            throw BizException.of(409, "租户编码已存在");
        }
        Tenant tenant =
                Tenant.create(
                        DistributedIdGenerator.generateLongId(),
                        cmd.getName(),
                        cmd.getCode(),
                        cmd.getLevel() != null ? cmd.getLevel() : 0,
                        cmd.getAdminEmail());
        return tenantRepository.save(tenant);
    }
}
