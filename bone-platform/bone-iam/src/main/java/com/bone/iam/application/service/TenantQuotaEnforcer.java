package com.bone.iam.application.service;

import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.common.IamErrors;
import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.repository.AccountRepository;
import com.bone.iam.domain.repository.RoleRepository;
import com.bone.iam.domain.repository.TenantRepository;
import com.bone.iam.domain.role.Role;
import com.bone.iam.domain.tenant.Tenant;
import com.bone.metadata.sdk.query.criteria.Criteria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 租户配额校验（S2，仅 CommandHandler 内部复用）。 */
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
    Criteria<Account> criteria = Criteria.<Account>create().eq("tenantId", tenantId);
    long count = accountRepository.countByCriteria(criteria);
    if (count >= tenant.getMaxAccounts()) {
      throw IamErrors.of(IamErrorCodes.TENANT_QUOTA_EXCEEDED, "账号数已达租户配额上限");
    }
  }

  public void assertCanAddRole(long tenantId) {
    Tenant tenant = tenantRepository.findById(tenantId);
    if (tenant == null || tenant.getMaxRoles() == null) {
      return;
    }
    Criteria<Role> criteria = Criteria.<Role>create().eq("tenantId", tenantId);
    long count = roleRepository.countByCriteria(criteria);
    if (count >= tenant.getMaxRoles()) {
      throw IamErrors.of(IamErrorCodes.TENANT_QUOTA_EXCEEDED, "角色数已达租户配额上限");
    }
  }
}
