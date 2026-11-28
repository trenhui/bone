package com.bone.claim.application;

import com.bone.claim.domain.model.claim.event.ClaimStatusChangedEvent;
import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import com.bone.claim.domain.model.task.TaskType;
import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.tenant.context.TenantContextRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 案件进入待处理池时开启任务（AUTO_ASSIGN 的写入入口）。
 *
 * <p><b>为什么用事件监听而不是在收单服务里加一行调用</b>：{@code ClaimCaseApplicationService} 是四批共享的热点文件（plan 第 8
 * 节要求共享文件串行合并、基于 HEAD 追加）。用事务提交后事件接入， 既不修改该文件、也不改变它的构造参数与事务边界——批 2/3/4 并行时冲突面为零。
 *
 * <p><b>为什么 AFTER_COMMIT</b>：任务必须在案件状态<b>真实落库后</b>才存在。若在同一事务内建任务， 案件后续步骤失败回滚会留下一条指向不存在案件的任务（脏归属）；而
 * AFTER_COMMIT 之后失败只会少建一条任务， 由监控与「入池兜底」可见，不会产生矛盾数据。
 *
 * <p><b>失败为什么吞掉并打 ERROR</b>：提交后抛异常无法回滚已提交的案件，只会把「任务没建」伪装成 「收单失败」误导调用方重试（重试会撞幂等键）。故记 ERROR 日志交监控——与
 * {@code ClaimCaseTrackRecorder} 的纪律一致：派生台账失败不得回滚主流程，但必须可被发现。
 */
@NoDomainEvent
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimTaskOpener {

  private final ClaimTaskApplicationService claimTaskApplicationService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onClaimStatusChanged(ClaimStatusChangedEvent event) {
    if (event == null
        || (event.toStatus() != ClaimStatus.PENDING_ADJUST
            && event.toStatus() != ClaimStatus.AUTO_PRE_EXAMINING)) {
      return;
    }
    try {
      // 自动签收链路停在 AUTO_PRE_EXAMINING，不会走到 PENDING_ADJUST；若只监听后者，
      // 自动初审案件在待办里根本没有任务，只能靠人工补建。两个入口共用 openForClaim 的
      // 「同案同类型生效任务」幂等，后续转 PENDING_ADJUST 时不会重复建第二条。
      // AFTER_COMMIT 后线程上下文已清空：写路径必须显式声明租户（ADR-0029 失败关闭），
      // 否则 SDK 的 AUTO 租户注入拿不到租户，插入会失败或被拒。
      TenantContextRunner.runAs(
          event.tenantId(),
          () -> claimTaskApplicationService.openForClaim(event.claimId(), TaskType.INITIAL_REVIEW));
    } catch (Exception e) {
      log.error(
          "[TASK_OPEN] 案件进入待理算池但任务开启失败（案件已提交，需人工/补偿建任务）: claimId={}, tenantId={}",
          event.claimId(),
          event.tenantId(),
          e);
    }
  }
}
