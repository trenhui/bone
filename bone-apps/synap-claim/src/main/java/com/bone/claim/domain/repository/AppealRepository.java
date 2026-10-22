package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.appeal.AppealRecord;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;

/**
 * A8 申诉仓储。自定义 SQL 按文件名约定绑定 {@code resources/sql/…/AppealRepository/<方法名>.sql}（与
 * AdjustmentRepository 同款，SDK 无 @SqlQuery 注解）。
 */
@TenantScope(TenantScopeMode.AUTO)
public interface AppealRepository extends Repository<AppealRecord, Long> {

  AppealRecord findLatestByClaimId(@Param("claimId") long claimId);
}
