package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.reconciliation.ReconciliationAction;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.util.List;

/**
 * 对账处置流水仓储（{@code t_reconciliation_action}，append-only）。
 *
 * <p>只提供追加与按目标回溯，<b>没有</b> UPDATE/DELETE 方法：处置流水是可审计事实，一经落库不可改写。 要修正一个处置结论，只能再追加一条（历史两条都在，读侧取最新）。
 */
@TenantScope(TenantScopeMode.AUTO)
public interface ReconciliationActionRepository extends Repository<ReconciliationAction, Long> {

  /** 某个目标（差异 / 额度账户）的全部处置动作，按发生顺序升序。 */
  @TenantScope(TenantScopeMode.AUTO)
  List<ReconciliationAction> findByTarget(
      @Param("targetType") String targetType, @Param("targetId") long targetId);
}
