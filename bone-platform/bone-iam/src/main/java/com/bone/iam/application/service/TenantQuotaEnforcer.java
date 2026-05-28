package com.bone.iam.application.service;

import com.bone.core.exception.BizException;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.repository.TenantRepository;
import com.bone.iam.domain.role.Role;
import com.bone.iam.domain.tenant.Tenant;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 租户配额校验（S2，仅 CommandHandler 内部复用）。
 */
@Service
@RequiredArgsConstructor
public class TenantQuotaEnforcer {

    private final TenantRepository tenantRepository;

    public void assertCanAddAccount(long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId);
        if (tenant == null || tenant.getMaxAccounts() == null) {
            return;
        }
        long count = QueryBuilder.from(Account.class)
                .where(Account::getTenantId)
                .eq(tenantId)
                .count();
        if (count >= tenant.getMaxAccounts()) {
            throw BizException.of(
                    400, IamErrorCodes.TENANT_QUOTA_EXCEEDED + ": 账号数已达租户配额上限");
        }
    }

    public void assertCanAddRole(long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId);
        if (tenant == null || tenant.getMaxRoles() == null) {
            return;
        }
        long count =
                QueryBuilder.from(Role.class).where(Role::getTenantId).eq(tenantId).count();
        if (count >= tenant.getMaxRoles()) {
            throw BizException.of(
                    400, IamErrorCodes.TENANT_QUOTA_EXCEEDED + ": 角色数已达租户配额上限");
        }
    }
}
