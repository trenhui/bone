package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.domain.model.task.ClaimTask;
import com.bone.claim.domain.model.task.ClaimTaskStatus;
import com.bone.claim.domain.repository.ClaimTaskRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 超时回池幂等判据钉子（批 4 任务 3.3，对应 5.2 变异点）。 */
@ExtendWith(MockitoExtension.class)
class TaskRequeueServiceTest {

  private static final long TASK_ID = 5001L;
  private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

  @Mock private ClaimTaskRepository taskRepository;

  private TaskRequeueService service;

  @BeforeEach
  void setUp() {
    service = new TaskRequeueService(taskRepository);
  }

  /** 构造一个 ASSIGNED 且未领取的任务。 */
  private static ClaimTask assignedTask() {
    ClaimTask task;
    try {
      var ctor = ClaimTask.class.getDeclaredConstructor();
      ctor.setAccessible(true);
      task = ctor.newInstance();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    ReflectionTestUtils.setField(task, "id", TASK_ID);
    ReflectionTestUtils.setField(task, "status", ClaimTaskStatus.ASSIGNED.name());
    ReflectionTestUtils.setField(task, "assigneeId", "user-A");
    ReflectionTestUtils.setField(task, "claimedAt", null);
    return task;
  }

  @Test
  @DisplayName("3.3 ASSIGNED 未领取：回池成功，回到 PENDING_CLAIM 且清归属")
  void assignedUnclaimedRequeued() {
    ClaimTask task = assignedTask();
    when(taskRepository.findById(TASK_ID)).thenReturn(task);

    boolean changed = service.requeueExpired(TASK_ID, NOW);

    assertThat(changed).isTrue();
    assertThat(task.getStatus()).isEqualTo(ClaimTaskStatus.PENDING_CLAIM.name());
    assertThat(task.getAssigneeId()).isNull();
    verify(taskRepository).update(task);
  }

  @Test
  @DisplayName("3.3 幂等：再次扫描（已 PENDING_CLAIM）不再回池、不重复 update")
  void repeatedScanIsIdempotent() {
    ClaimTask task = assignedTask();
    when(taskRepository.findById(TASK_ID)).thenReturn(task);

    assertThat(service.requeueExpired(TASK_ID, NOW)).isTrue();
    // 第二次：任务已是 PENDING_CLAIM，requeueIfExpired 返回 false
    assertThat(service.requeueExpired(TASK_ID, NOW)).isFalse();
    // update 只调用一次（幂等证据：重复扫描不产生第二条回池事实）
    verify(taskRepository, times(1)).update(any(ClaimTask.class));
  }

  @Test
  @DisplayName("3.3 PROCESSING（已领取）任务不被回池：保护在办任务不被抢回池")
  void processingTaskNotRequeued() {
    ClaimTask task = assignedTask();
    ReflectionTestUtils.setField(task, "status", ClaimTaskStatus.PROCESSING.name());
    ReflectionTestUtils.setField(task, "claimedAt", NOW);
    when(taskRepository.findById(TASK_ID)).thenReturn(task);

    assertThat(service.requeueExpired(TASK_ID, NOW)).isFalse();
    verify(taskRepository, times(0)).update(any(ClaimTask.class));
  }
}
