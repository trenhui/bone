package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.inspection.ClaimTaskTransfer;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.util.List;

/**
 * 任务转交留痕仓储（{@code t_claim_task_transfer}，append-only）。
 *
 * <p>转出/转入/原因/时间是责任证据，一经落库不可改写；要纠正只能再追加一条（读侧取最新）。
 */
@TenantScope(TenantScopeMode.AUTO)
public interface ClaimTaskTransferRepository extends Repository<ClaimTaskTransfer, Long> {

  /** 任务全部转交记录，按时间倒序（读侧取第一条即当前归属）。 */
  List<ClaimTaskTransfer> findByTaskId(@Param("taskId") long taskId);
}
