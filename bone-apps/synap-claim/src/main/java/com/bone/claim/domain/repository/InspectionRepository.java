package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.inspection.InspectionRecord;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;

/**
 * 后督抽检仓储。自定义 SQL 按文件名约定绑定 {@code resources/sql/…/InspectionRepository/<方法名>.sql}（SDK 无
 * {@code @SqlQuery} 注解，与 {@code AppealRepository} 同款）。
 *
 * <p>租户口径 {@link TenantScopeMode#AUTO}：SQL 模板内以 {@code /*bone:tenant*}{@code /} 锚点注入租户条件——抽检单跨租户
 * 可见即构成越权读取（抽检单里含案件号与差异金额）。
 */
@TenantScope(TenantScopeMode.AUTO)
public interface InspectionRepository extends Repository<InspectionRecord, Long> {

  /** 按抽检单号查（对外引用键；租户内唯一）。 */
  InspectionRecord findByInspectionNo(@Param("inspectionNo") String inspectionNo);

  /** 按案件查最新抽检单（一期一案一检，用于重复发起幂等）。 */
  InspectionRecord findLatestByClaimId(@Param("claimId") long claimId);
}
