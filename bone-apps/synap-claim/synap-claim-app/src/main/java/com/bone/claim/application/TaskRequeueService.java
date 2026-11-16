package com.bone.claim.application;

import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.task.ClaimTask;
import com.bone.claim.domain.repository.ClaimTaskRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 超时回池应用服务（批 4 任务 3.3）：把「已指派但始终未领取且超时」的任务幂等回池。
 *
 * <p>幂等由两道保证：① 领域方法 {@link ClaimTask#requeueIfExpired} 只在 ASSIGNED + 未领取时返回 true，重复调用返回 false； ②
 * 任务聚合的 {@code @Version} 乐观锁——并发重复扫描时只有一个 update 生效，另一个抛冲突并映射为「跳过」而非失败。 必须在调用方（定时 Job）显式 {@code
 * TenantContextRunner.runAs} 后调用，写路径租户失败关闭。
 */
@Service
@RequiredArgsConstructor
public class TaskRequeueService {

  private final ClaimTaskRepository taskRepository;

  /** 回池单条任务；返回 true 表示本次真的回池，false 表示幂等跳过（状态不满足或乐观锁冲突）。 */
  @Transactional
  public boolean requeueExpired(long taskId, Instant now) {
    ClaimTask task = taskRepository.findById(taskId);
    if (task == null) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_TASK_NOT_FOUND, taskId);
    }
    if (!task.requeueIfExpired(now)) {
      return false;
    }
    try {
      taskRepository.update(task);
    } catch (org.springframework.dao.OptimisticLockingFailureException e) {
      // 并发回池：另一实例已处理，幂等跳过（不重复回池、不产生第二条事实）
      return false;
    }
    return true;
  }
}
