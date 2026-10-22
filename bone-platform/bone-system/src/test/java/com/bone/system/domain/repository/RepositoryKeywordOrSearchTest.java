package com.bone.system.domain.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.model.PageResult;
import com.bone.core.tenant.context.TenantContext;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.system.domain.model.config.SystemConfig;
import com.bone.system.domain.model.config.valueobject.ConfigKey;
import com.bone.system.domain.model.config.valueobject.ConfigType;
import com.bone.system.domain.model.config.valueobject.ConfigValue;
import com.bone.system.domain.model.schedule.ScheduleTask;
import com.bone.system.domain.model.schedule.valueobject.TaskStatus;
import com.bone.system.testsupport.MetadataSdkIntegrationTestConfiguration;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 关键词跨列 OR 分页的真实执行测试（H2 + metadata-sdk，非 mock）。
 *
 * <p>覆盖两类聚合的关键词分页（{@link ScheduleTaskRepository#pageByKeywordAndStatus} 与 {@link
 * SystemConfigRepository#pageByKeyword}），重点是 <b>OR 条件组与后续等值过滤的 SQL 优先级</b>： QueryBuilder 扁平 {@code
 * or()} 链会退化为 {@code a OR (b AND c)}，导致「仅首列命中、却不满足过滤条件」的行漏过过滤。 mock 仓储验不到这一层，必须走真实 SQL。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(MetadataSdkIntegrationTestConfiguration.class)
@Transactional
public class RepositoryKeywordOrSearchTest {

  @BeforeEach
  void setUpTenantContext() {
    TenantContext.setTenantId(0L);
  }

  @AfterEach
  void tearDownTenantContext() {
    TenantContext.clear();
  }

  @Autowired private ScheduleTaskRepository scheduleTaskRepository;

  @Autowired private SystemConfigRepository systemConfigRepository;

  // ==================== 定时任务：名称 OR 处理器 ====================

  private void seedTasks() {
    saveTask("报表巡检", "patrolJobHandler", TaskStatus.ENABLED);
    saveTask("报表生成", "nightlyReportJobHandler", TaskStatus.DISABLED);
    saveTask("夜间归档", "nightlyArchiveJobHandler", TaskStatus.ENABLED);
  }

  private void saveTask(String name, String handler, TaskStatus status) {
    scheduleTaskRepository.save(
        ScheduleTask.create(
            DistributedIdGenerator.generateLongId(), 0L, name, "0 0 2 * * ?", handler, status));
  }

  @Test
  @DisplayName("关键词命中处理器列（handler 维度）——UI placeholder 承诺的两维搜索之一")
  void scheduleKeywordMatchesHandlerColumn() {
    seedTasks();

    PageResult<ScheduleTask> page =
        scheduleTaskRepository.pageByKeywordAndStatus("nightlyReportJob", null, 1, 10);

    assertEquals(1, page.getTotal());
    assertEquals("报表生成", page.getRecords().get(0).getName());
  }

  @Test
  @DisplayName("关键词命中名称列（name 维度）——回归原有能力")
  void scheduleKeywordMatchesNameColumn() {
    seedTasks();

    PageResult<ScheduleTask> page =
        scheduleTaskRepository.pageByKeywordAndStatus("报表", null, 1, 10);

    assertEquals(2, page.getTotal());
  }

  @Test
  @DisplayName("优先级回归：名称命中的停用任务不得漏过状态过滤（flat OR 会泄漏的场景）")
  void scheduleStatusFilterMustNotBeBypassedByNameMatch() {
    seedTasks();

    // 「报表生成」名称命中但状态为 DISABLED；扁平 or() 链会生成
    // name LIKE ? OR (handler LIKE ? AND status = ?)，该行从 OR 左支漏过。
    PageResult<ScheduleTask> page =
        scheduleTaskRepository.pageByKeywordAndStatus("报表", TaskStatus.ENABLED, 1, 10);

    assertEquals(1, page.getTotal());
    assertEquals("报表巡检", page.getRecords().get(0).getName());
  }

  @Test
  @DisplayName("优先级回归：处理器命中的行同样受状态过滤约束")
  void scheduleStatusFilterAlsoAppliesToHandlerMatch() {
    seedTasks();

    // nightlyReportJobHandler 属于 DISABLED 任务；nightlyArchiveJobHandler 属于 ENABLED 任务。
    PageResult<ScheduleTask> page =
        scheduleTaskRepository.pageByKeywordAndStatus("nightly", TaskStatus.ENABLED, 1, 10);

    assertEquals(1, page.getTotal());
    assertEquals("夜间归档", page.getRecords().get(0).getName());
  }

  @Test
  @DisplayName("空关键词等价于全量分页（仅状态过滤）——三行全回不重不漏")
  void scheduleBlankKeywordReturnsAll() {
    seedTasks();

    PageResult<ScheduleTask> page =
        scheduleTaskRepository.pageByKeywordAndStatus("  ", null, 1, 10);

    assertEquals(3, page.getTotal());
    // 刻意用集合而非序列断言：ScheduleTask.create 内部置 createdAt=now() 且无注入口，
    // 连续插入的时间戳理论上可能同毫秒，严格倒序断言会 flake（倒序由 live E2E 目验）。
    assertEquals(
        Set.of("报表巡检", "报表生成", "夜间归档"),
        page.getRecords().stream()
            .map(ScheduleTask::getName)
            .collect(java.util.stream.Collectors.toSet()));
  }

  // ==================== 系统配置：配置键 OR 描述 ====================

  private void seedConfigs() {
    saveConfig("sync.interval", "数据同步间隔", ConfigType.SYSTEM);
    saveConfig("report.title", "报表标题", ConfigType.FEATURE);
  }

  private void saveConfig(String key, String description, ConfigType type) {
    systemConfigRepository.save(
        SystemConfig.create(
            DistributedIdGenerator.generateLongId(),
            ConfigKey.of(key),
            ConfigValue.of("v"),
            description,
            type,
            false,
            "test",
            0L));
  }

  @Test
  @DisplayName("配置：关键词命中描述列，类型过滤精确生效")
  void configKeywordMatchesDescriptionWithTypeFilter() {
    seedConfigs();

    PageResult<SystemConfig> page =
        systemConfigRepository.pageByKeyword("报表", ConfigType.FEATURE, 1, 10);

    assertEquals(1, page.getTotal());
    assertEquals("report.title", page.getRecords().get(0).getConfigKey().value());
  }

  @Test
  @DisplayName("配置：优先级回归——键命中的异类型配置不得漏过类型过滤（flat OR 会泄漏的场景）")
  void configTypeFilterMustNotBeBypassedByKeyMatch() {
    seedConfigs();

    // report.title 的键命中「report」，但它是 FEATURE 类型；扁平 or() 链会让它从 OR 左支漏过 SYSTEM 过滤。
    PageResult<SystemConfig> page =
        systemConfigRepository.pageByKeyword("report", ConfigType.SYSTEM, 1, 10);

    assertEquals(0, page.getTotal());
    assertTrue(page.getRecords().isEmpty());
  }

  @Test
  @DisplayName("配置：空关键词按类型全量分页")
  void configBlankKeywordReturnsAllOfType() {
    seedConfigs();

    PageResult<SystemConfig> page =
        systemConfigRepository.pageByKeyword(null, ConfigType.SYSTEM, 1, 10);

    assertEquals(1, page.getTotal());
    assertEquals("sync.interval", page.getRecords().get(0).getConfigKey().value());
  }
}
