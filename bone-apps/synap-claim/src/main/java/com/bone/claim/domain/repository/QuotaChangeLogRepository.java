package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.quota.QuotaChangeLog;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.util.List;

/**
 * 额度保全记录仓储（{@code t_quota_change_log}）。
 *
 * <p>只读用途为主（配置面展示"谁在什么时候把额度从 A 调到 B"），写入由应用层直接落库。
 */
@TenantScope(TenantScopeMode.AUTO)
public interface QuotaChangeLogRepository extends Repository<QuotaChangeLog, Long> {

  /** 按额度账户取保全记录（时间正序）。 */
  @TenantScope(TenantScopeMode.AUTO)
  List<QuotaChangeLog> findByAccountId(@Param("quotaAccountId") long quotaAccountId);

  /** 按保单号取保全记录（配置面：某保单下的全部额度变更）。 */
  @TenantScope(TenantScopeMode.AUTO)
  List<QuotaChangeLog> findByPolicyNo(@Param("policyNo") String policyNo);
}
