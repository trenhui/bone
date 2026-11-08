package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.adjustment.Adjustment;
import com.bone.claim.domain.model.adjustment.AdjustmentLine;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.util.List;

/** A6 理算仓储（理算记录 + 明细行分开查询：与 blueprint 同款，集合不随聚合回填）。 */
@TenantScope(TenantScopeMode.AUTO)
public interface AdjustmentRepository extends Repository<Adjustment, Long> {

  Adjustment findById(@Param("calculationId") long calculationId);

  @TenantScope(TenantScopeMode.AUTO)
  Adjustment findLatestByClaimId(@Param("claimId") long claimId);

  @TenantScope(TenantScopeMode.AUTO)
  List<AdjustmentLine> findLinesByCalculationId(@Param("calculationId") long calculationId);

  /**
   * 作废某次理算的上一轮归因行（重算前调用）：重算用同一 calculation_id 重出一套归因，旧行必须随之退场。
   *
   * <p><b>为什么要显式清</b>：归因行经聚合级联写，关系的 {@code orphanRemoval} 是 {@code false} （否则 {@code confirm()}
   * 这类"只改状态、不带集合"的 save 会把归因行全软删掉）。代价是重算不会自动 清理上一轮的行——同一 {@code calculation_id}
   * 下会叠出多套互相矛盾的逐行归因（实测：配类别规则 算 3 行，撤规则重算又加 3 行 ⇒ 6 行），对账与后督抽检会读到两个"事实"。
   */
  @TenantScope(TenantScopeMode.AUTO)
  int supersedeLinesByCalculationId(@Param("calculationId") long calculationId);
}
