package com.bone.system.adapter.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.exception.BizException;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.tenant.context.TenantContext;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.system.adapter.web.dto.response.ScheduleTaskLogResp;
import com.bone.system.domain.model.schedule.ScheduleTask;
import com.bone.system.domain.model.schedule.valueobject.TaskStatus;
import com.bone.system.domain.repository.ScheduleTaskRepository;
import com.bone.system.testsupport.MetadataSdkIntegrationTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

/**
 * 「失败执行记录必须幸存调用方事务回滚」的真实事务集成测试（H2 + metadata-sdk）。
 *
 * <p><b>为什么这个测试不能省</b>：手动执行（{@code triggerNow}）跑在 {@code runNow} 的事务里，handler 缺失时异常抛回 ⇒ 调用方事务回滚。
 * 若执行流水与调用方同事务，失败记录会连同回滚一起消失——「失败可追溯」恰在最需要它的场景失灵（2026-10-22 联调实测复现过这个 bug， 修复为流水走 {@code
 * ScheduleTaskLogRecorder} 的 REQUIRES_NEW 独立事务）。
 *
 * <p><b>为什么刻意不加 {@code @Transactional}</b>：同类的 {@code ScheduleTaskExecutionsTest} 靠测试事务兜底隔离，
 * 而本测试的判据恰恰是「调用方事务回滚之后流水还在」——测试自己开事务会把回滚变成伪回滚（同连接内数据仍可见），bug 会被放过。故用显式 DELETE 清理代替事务回滚。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(MetadataSdkIntegrationTestConfiguration.class)
@WithMockUser(authorities = {"sys:schedule:read", "sys:schedule:write"})
public class ScheduleTaskFailedRunRecordTest {

  private static final String PROBE_NAME = "failedRunSurvivalProbe";

  @BeforeEach
  void setUpTenantContext() {
    TenantContext.setTenantId(0L);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    jdbcTemplate.update("DELETE FROM sys_schedule_task_log WHERE task_name = ?", PROBE_NAME);
    jdbcTemplate.update("DELETE FROM sys_schedule_task WHERE name = ?", PROBE_NAME);
  }

  @Autowired private ScheduleTaskController scheduleTaskController;

  @Autowired private ScheduleTaskRepository scheduleTaskRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  public void failedRunRecordSurvivesCallerTransactionRollback() {
    ScheduleTask task =
        ScheduleTask.create(
            DistributedIdGenerator.generateLongId(),
            0L,
            PROBE_NAME,
            "0 0 5 * * ?",
            "noSuchHandlerBeanXyz",
            TaskStatus.DISABLED);
    scheduleTaskRepository.save(task);

    // 处理器缺失 ⇒ 404 抛出 ⇒ runNow 的调用方事务回滚
    assertRunNowThrowsHandlerNotFound(task.getId());

    // 关键判据：回滚之后（全新读）流水仍在，且带失败详情
    ApiResponse<PageResult<ScheduleTaskLogResp>> resp =
        scheduleTaskController.executions(task.getId(), 1, 10);
    assertNotNull(resp.getData());
    assertEquals(1, resp.getData().getTotal());
    ScheduleTaskLogResp record = resp.getData().getRecords().get(0);
    assertEquals("FAILED", record.runStatus());
    assertEquals("MANUAL", record.triggerType());
    assertNotNull(record.durationMs());
    assertTrue(record.message().contains("noSuchHandlerBeanXyz"));
  }

  private void assertRunNowThrowsHandlerNotFound(Long taskId) {
    try {
      scheduleTaskController.runNow(taskId);
      throw new AssertionError("handler 缺失的执行必须抛 BizException");
    } catch (BizException e) {
      assertTrue(
          e.getMessage().contains("SYS_SCHEDULE_TASK_HANDLER_NOT_FOUND"),
          "应为 HANDLER_NOT_FOUND，实际: " + e.getMessage());
    }
  }
}
