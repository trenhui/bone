package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.inspection.ClaimTaskTransfer;
import com.bone.claim.domain.model.task.ClaimTask;
import com.bone.claim.domain.repository.ClaimTaskRepository;
import com.bone.claim.domain.repository.ClaimTaskTransferRepository;
import com.bone.core.util.DistributedIdGenerator;
import java.time.Instant;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务转交服务（批 4 任务 3.2）。
 *
 * <p>转出/转入/原因是责任证据：除了改任务上的当前归属投影（{@link ClaimTask#transferTo}），还必须 append 一条 {@link
 * ClaimTaskTransfer} 事实留痕。历史转交不可更新——仓储只暴露 insert 与按任务回溯，不提供改字段方法。
 */
@Service
@RequiredArgsConstructor
public class TaskTransferService {

  private final ClaimTaskRepository taskRepository;
  private final ClaimTaskTransferRepository transferRepository;
  private final TenantPort tenantPort;

  /** 转交结果视图（ID 一律 String）。 */
  public record TransferView(String transferId, String taskId, String toAssigneeId) {}

  /** 把任务转交给他人，并追加一条不可改写的转交留痕。 */
  @Transactional
  public TransferView transfer(long taskId, String toAssigneeId, String reason, String operatorId) {
    if (operatorId == null || operatorId.isBlank()) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_TASK_NOT_FOUND, "转交操作人必须取认证主体");
    }
    ClaimTask task = taskRepository.findById(taskId);
    if (task == null) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_TASK_NOT_FOUND, taskId);
    }
    String fromAssignee = task.getAssigneeId();
    Instant now = Instant.now();
    task.transferTo(toAssigneeId, reason, now);
    taskRepository.update(task);

    long tenantId = tenantPort.currentTenantId();
    ClaimTaskTransfer trace =
        ClaimTaskTransfer.record(
            DistributedIdGenerator.generateLongId(),
            tenantId,
            taskId,
            task.getClaimId(),
            fromAssignee,
            toAssigneeId,
            reason,
            operatorId,
            Date.from(now));
    transferRepository.insert(trace);
    return new TransferView(String.valueOf(trace.getId()), String.valueOf(taskId), toAssigneeId);
  }
}
