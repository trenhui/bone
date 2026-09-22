package com.bone.iam.application.policy;

import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.common.IamErrors;
import com.bone.iam.domain.model.tenant.Tenant;
import com.bone.iam.domain.repository.AccountRepository;
import com.bone.iam.domain.repository.RoleRepository;
import com.bone.iam.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 租户配额校验（S2，仅 CommandHandler 内部复用）。计数委托域仓储（E-4.2）。 */
@Service
@RequiredArgsConstructor
public class TenantQuotaEnforcer {

  private final TenantRepository tenantRepository;
  private final AccountRepository accountRepository;
  private final RoleRepository roleRepository;

  public void assertCanAddAccount(long tenantId) {
    Tenant tenant = tenantRepository.findById(tenantId);
    if (tenant == null || tenant.getMaxAccounts() == null) {
      return;
    }
    long count = accountRepository.countByTenant(tenantId);
    if (count >= tenant.getMaxAccounts()) {
      throw IamErrors.of(IamErrorCodes.TENANT_QUOTA_EXCEEDED, "账号数已达租户配额上限");
    }
  }

  public void assertCanAddRole(long tenantId) {
    Tenant tenant = tenantRepository.findById(tenantId);
    if (tenant == null || tenant.getMaxRoles() == null) {
      return;
    }
    long count = roleRepository.countByTenant(tenantId);
    if (count >= tenant.getMaxRoles()) {
      throw IamErrors.of(IamErrorCodes.TENANT_QUOTA_EXCEEDED, "角色数已达租户配额上限");
    }
  }
}
