package com.bone.system.adapter.web.assembler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.bone.system.adapter.web.dto.response.AlertRecordResp;
import com.bone.system.adapter.web.dto.response.AlertRuleResp;
import com.bone.system.adapter.web.dto.response.ConfigResp;
import com.bone.system.adapter.web.dto.response.DictTypeResp;
import com.bone.system.adapter.web.dto.response.LogResp;
import com.bone.system.adapter.web.dto.response.ScheduleTaskResp;
import com.bone.system.application.query.dto.AlertRecordDto;
import com.bone.system.application.query.dto.AlertRuleDto;
import com.bone.system.application.query.dto.ConfigDto;
import com.bone.system.application.query.dto.DictTypeDto;
import com.bone.system.application.query.dto.LogDto;
import com.bone.system.application.query.dto.ScheduleTaskDto;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 时间字段 UTC 契约测试（i18n 方案 §6.4）：对外响应里的 {@code LocalDateTime} 必须在 assembler 边界转为 {@link Instant}（UTC
 * 归一），序列化为带偏移的 ISO-8601（{@code ...Z}），否则前端 {@code dayjs.utc(v)} 会静默 +8h。
 *
 * <p>回归防护：若删掉各 assembler 的 {@code toInstant(LocalDateTime)} 默认方法，MapStruct 仍会编译通过，
 * 但响应时间字段会变成无偏移字符串，CI 脚本查不出——必须用<b>行为级</b>断言钉死口径。
 */
class UtcTimeContractTest {

  private static final LocalDateTime SAMPLE = LocalDateTime.of(2026, 9, 25, 12, 0, 0);
  private static final Instant EXPECTED = Instant.parse("2026-09-25T12:00:00Z");

  @Test
  void configRespTimeIsUtcInstant() {
    ConfigResp resp =
        new ConfigAssemblerImpl()
            .toResp(ConfigDto.builder().createdAt(SAMPLE).updatedAt(SAMPLE).build());
    assertEquals(EXPECTED, resp.getCreatedAt());
    assertEquals(EXPECTED, resp.getUpdatedAt());
  }

  @Test
  void alertRecordRespTimeIsUtcInstant() {
    AlertRecordResp resp =
        new AlertAssemblerImpl()
            .toResp(AlertRecordDto.builder().createdAt(SAMPLE).resolveTime(SAMPLE).build());
    assertEquals(EXPECTED, resp.getCreatedAt());
    assertEquals(EXPECTED, resp.getResolveTime());
  }

  @Test
  void alertRuleRespTimeIsUtcInstant() {
    AlertRuleResp resp =
        new AlertAssemblerImpl()
            .toResp(AlertRuleDto.builder().createdAt(SAMPLE).updatedAt(SAMPLE).build());
    assertEquals(EXPECTED, resp.getCreatedAt());
    assertEquals(EXPECTED, resp.getUpdatedAt());
  }

  @Test
  void dictRespTimeIsUtcInstant() {
    DictTypeResp resp =
        new DictAssemblerImpl()
            .toResp(DictTypeDto.builder().createdAt(SAMPLE).updatedAt(SAMPLE).build());
    assertEquals(EXPECTED, resp.getCreatedAt());
    assertEquals(EXPECTED, resp.getUpdatedAt());
  }

  @Test
  void scheduleTaskRespTimeIsUtcInstant() {
    ScheduleTaskResp resp =
        new ScheduleTaskAssemblerImpl()
            .toResp(
                ScheduleTaskDto.builder()
                    .lastRunAt(SAMPLE)
                    .nextRunAt(SAMPLE)
                    .createdAt(SAMPLE)
                    .updatedAt(SAMPLE)
                    .build());
    assertEquals(EXPECTED, resp.getLastRunAt());
    assertEquals(EXPECTED, resp.getNextRunAt());
    assertEquals(EXPECTED, resp.getCreatedAt());
    assertEquals(EXPECTED, resp.getUpdatedAt());
  }

  @Test
  void logRespTimeIsUtcInstant() {
    LogResp resp = new LogAssemblerImpl().toResp(LogDto.builder().createdAt(SAMPLE).build());
    assertEquals(EXPECTED, resp.getCreatedAt());
  }

  @Test
  void nullLocalDateTimeMapsToNullInstant() {
    ConfigResp resp = new ConfigAssemblerImpl().toResp(ConfigDto.builder().build());
    assertNull(resp.getCreatedAt());
  }
}
