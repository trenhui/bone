package com.bone.system.adapter.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.tenant.context.TenantContext;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.system.adapter.web.dto.response.ScheduleTaskLogResp;
import com.bone.system.domain.model.schedule.ScheduleTask;
import com.bone.system.domain.model.schedule.ScheduleTaskLog;
import com.bone.system.domain.model.schedule.valueobject.TaskRunStatus;
import com.bone.system.domain.model.schedule.valueobject.TaskStatus;
import com.bone.system.domain.model.schedule.valueobject.TriggerType;
import com.bone.system.domain.repository.ScheduleTaskLogRepository;
import com.bone.system.domain.repository.ScheduleTaskRepository;
import com.bone.system.testsupport.MetadataSdkIntegrationTestConfiguration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 执行记录分页接口的真实执行测试（H2 + metadata-sdk，非 mock）。
 *
 * <p>刻意走真实 SDK 查询而不是 mock 仓储：{@code ScheduleTaskLogRepository#pageByTask} 的租户过滤（ADR-0029） 与倒序分页都依赖
 * SDK Criteria 行为，mock 掉它们等于什么都没验。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(MetadataSdkIntegrationTestConfiguration.class)
@WithMockUser(authorities = {"sys:schedule:read"})
@Transactional
public class ScheduleTaskExecutionsTest {

  /** 与建数据用的 tenantId 一致（平台租户）。 */
  @BeforeEach
  void setUpTenantContext() {
    TenantContext.setTenantId(0L);
  }

  @AfterEach
  void tearDownTenantContext() {
    TenantContext.clear();
  }

  @Autowired private ScheduleTaskController scheduleTaskController;

  @Autowired private ScheduleTaskRepository scheduleTaskRepository;

  @Autowired private ScheduleTaskLogRepository scheduleTaskLogRepository;

  @Test
  public void executionsPageReturnsNewestFirstWithAllFields() {
    ScheduleTask task =
        ScheduleTask.create(
            DistributedIdGenerator.generateLongId(),
            0L,
            "报表生成",
            "0 0 2 * * ?",
            "reportJob",
            TaskStatus.ENABLED);
    scheduleTaskRepository.save(task);
    Long taskId = task.getId();
    assertNotNull(taskId);

    LocalDateTime base = LocalDateTime.of(2026, 10, 22, 2, 0, 0);
    saveLog(taskId, base, TaskRunStatus.FAILED, "IllegalStateException: boom");
    saveLog(taskId, base.plusMinutes(1), TaskRunStatus.SUCCESS, null);
    saveLog(taskId, base.plusMinutes(2), TaskRunStatus.SUCCESS, null);

    ApiResponse<PageResult<ScheduleTaskLogResp>> resp =
        scheduleTaskController.executions(taskId, 1, 10);
    assertTrue(resp.isSuccess());
    PageResult<ScheduleTaskLogResp> page = resp.getData();
    assertEquals(3, page.getTotal());
    List<ScheduleTaskLogResp> records = page.getRecords();
    assertEquals(3, records.size());

    // 倒序：最新执行在前
    assertTrue(records.get(0).startedAt().isAfter(records.get(2).startedAt()));
    // 失败记录的消息与状态完整带回（观测的价值全在失败可追溯）
    ScheduleTaskLogResp failed = records.get(2);
    assertEquals("FAILED", failed.runStatus());
    assertEquals("IllegalStateException: boom", failed.message());
    assertEquals("MANUAL", failed.triggerType());
    assertNotNull(failed.durationMs());
  }

  /** 无执行记录的任务返回空页（不是 null，也不是错误）。 */
  @Test
  public void executionsPageEmptyForTaskWithoutRuns() {
    ScheduleTask task =
        ScheduleTask.create(
            DistributedIdGenerator.generateLongId(),
            0L,
            "从未执行",
            "0 0 3 * * ?",
            "reportJob",
            TaskStatus.DISABLED);
    scheduleTaskRepository.save(task);

    ApiResponse<PageResult<ScheduleTaskLogResp>> resp =
        scheduleTaskController.executions(task.getId(), 1, 10);

    assertTrue(resp.isSuccess());
    assertEquals(0, resp.getData().getTotal());
    assertTrue(resp.getData().getRecords().isEmpty());
  }

  private void saveLog(Long taskId, LocalDateTime startedAt, TaskRunStatus status, String message) {
    ScheduleTaskLog log =
        ScheduleTaskLog.record(
            DistributedIdGenerator.generateLongId(),
            0L,
            taskId,
            "报表生成",
            "reportJob",
            TriggerType.MANUAL,
            status,
            startedAt,
            startedAt.plusSeconds(2),
            message);
    scheduleTaskLogRepository.save(log);
  }
}
