package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.inspection.ClaimInspectionAction;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.util.List;

/**
 * 抽检处置留痕仓储（{@code t_claim_inspection_action}，append-only）。
 *
 * <p>只暴露追加（继承 {@link Repository#insert}）与按案件回溯，<b>不声明</b> UPDATE/DELETE：处置是审计事实，
 * 打回原因/抽检人/冲正前置一旦落库不可改写。自定义 SQL 按文件名约定绑定 {@code
 * resources/sql/…/ClaimInspectionActionRepository/<方法名>.sql}。
 */
@TenantScope(TenantScopeMode.AUTO)
public interface ClaimInspectionActionRepository extends Repository<ClaimInspectionAction, Long> {

  /** 案件的全部处置，按时间倒序（读侧取第一条即最新处置）。 */
  List<ClaimInspectionAction> findByClaimId(@Param("claimId") long claimId);
}
