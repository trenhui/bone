package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.config.FeatureToggle;
import com.bone.claim.domain.model.task.AssignReason;
import com.bone.claim.domain.model.task.AssigneeType;
import com.bone.claim.domain.model.task.ClaimAssignRule;
import com.bone.claim.domain.model.task.ClaimTask;
import com.bone.claim.domain.model.task.ClaimTaskStatus;
import com.bone.claim.domain.model.task.TaskAssignmentContext;
import com.bone.claim.domain.model.task.TaskType;
import com.bone.claim.domain.port.out.AssigneeEligibilityPort;
import com.bone.claim.domain.repository.ClaimAssignRuleRepository;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.ClaimTaskRepository;
import com.bone.core.exception.BizException;
import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.domain.exception.OptimisticLockingFailureException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 任务应用服务（任务 2.4／3.2 的判据钉子）。
 *
 * <p>钉三件事：
 *
 * <ol>
 *   <li><b>开关关闭仍建任务</b>：AUTO_ASSIGN 关闭只停写归属，任务必须存在（否则关闭开关 = 抹掉案件）；
 *   <li><b>冲突语义</b>：乐观锁失败 / 状态已领取都映射 409 {@code SC_CLAIM_TASK_CONFLICT}，且不重试；
 *   <li><b>total 同源</b>：find 与 count 共用同一组过滤实参（口径漂移的来源是「两处各写一遍条件」）。
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClaimTaskApplicationServiceTest {

  private static final long TENANT = 10L;
  private static final long CLAIM_ID = 100L;
  private static final Instant NOW = Instant.parse("2026-11-16T02:00:00Z");

  @Mock private ClaimTaskRepository taskRepository;
  @Mock private ClaimAssignRuleRepository ruleRepository;
  @Mock private ClaimCaseRepository claimCaseRepository;
  @Mock private AssigneeEligibilityPort eligibility;
  @Mock private FeatureTenantToggleService toggleService;
  @Mock private TenantPort tenantPort;
  @Mock private ClaimCase claimCase;

  private ClaimTaskApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new ClaimTaskApplicationService(
            taskRepository,
            ruleRepository,
            claimCaseRepository,
            eligibility,
            toggleService,
            tenantPort);
    lenient().when(tenantPort.currentTenantId()).thenReturn(TENANT);
    lenient().when(claimCaseRepository.findById(CLAIM_ID)).thenReturn(claimCase);
    lenient().when(claimCase.getClaimType()).thenReturn("OUTPATIENT");
    lenient().when(claimCase.getSourceChannel()).thenReturn("APP");
    lenient().when(claimCase.getClaimAmount()).thenReturn(new BigDecimal("1200.00"));
    lenient()
        .when(taskRepository.findActiveByClaimAndType(anyLong(), anyString()))
        .thenReturn(null);
  }

  private static ClaimAssignRule rule(long id, String assigneeId) {
    return ClaimAssignRule.create(
        id,
        TENANT,
        "r" + id,
        TaskType.INITIAL_REVIEW.name(),
        "",
        "",
        null,
        null,
        AssigneeType.USER.name(),
        assigneeId,
        null,
        10,
        true,
        NOW);
  }

  private ClaimTask capturedTask() {
    ArgumentCaptor<ClaimTask> captor = ArgumentCaptor.forClass(ClaimTask.class);
    verify(taskRepository).insert(captor.capture());
    return captor.getValue();
  }

  @Test
  @DisplayName("开关关闭：任务仍以入池形态创建（关闭不等于不建任务）")
  void openForClaim_toggleOff_createsPoolTask() {
    when(toggleService.isFeatureAllowed(TENANT, true, FeatureToggle.FEATURE_AUTO_ASSIGN))
        .thenReturn(false);

    ClaimTaskApplicationService.MyTaskRow row =
        service.openForClaim(CLAIM_ID, TaskType.INITIAL_REVIEW);

    ClaimTask saved = capturedTask();
    assertThat(saved.getStatus()).isEqualTo(ClaimTaskStatus.PENDING_CLAIM.name());
    assertThat(saved.getAssigneeId()).isNull();
    assertThat(saved.getAssignReason()).isEqualTo(AssignReason.NO_RULE.name());
    assertThat(row.status()).isEqualTo(ClaimTaskStatus.PENDING_CLAIM.name());
    // 开关关闭时不得触碰规则表（省一次查询，也让「关闭」= 完全不求解这一点可验证）
    verify(ruleRepository, never()).findCandidateRules(anyString());
  }

  @Test
  @DisplayName("开关开启 + 规则命中：写入归属与规则ID，状态 ASSIGNED")
  void openForClaim_ruleMatched_assigns() {
    when(toggleService.isFeatureAllowed(TENANT, true, FeatureToggle.FEATURE_AUTO_ASSIGN))
        .thenReturn(true);
    when(ruleRepository.findCandidateRules(TaskType.INITIAL_REVIEW.name()))
        .thenReturn(List.of(rule(7L, "u-7")));
    when(eligibility.isEligible(AssigneeType.USER, "u-7")).thenReturn(true);

    service.openForClaim(CLAIM_ID, TaskType.INITIAL_REVIEW);

    ClaimTask saved = capturedTask();
    assertThat(saved.getStatus()).isEqualTo(ClaimTaskStatus.ASSIGNED.name());
    assertThat(saved.getAssigneeId()).isEqualTo("u-7");
    assertThat(saved.getAssignRuleId()).isEqualTo(7L);
    assertThat(saved.getAssignReason()).isEqualTo(AssignReason.RULE_MATCH.name());
  }

  @Test
  @DisplayName("候选全不可用：入池并记 ALL_INELIGIBLE（不臆造归属）")
  void openForClaim_allIneligible_pools() {
    when(toggleService.isFeatureAllowed(TENANT, true, FeatureToggle.FEATURE_AUTO_ASSIGN))
        .thenReturn(true);
    when(ruleRepository.findCandidateRules(TaskType.INITIAL_REVIEW.name()))
        .thenReturn(List.of(rule(7L, "u-7")));
    when(eligibility.isEligible(AssigneeType.USER, "u-7")).thenReturn(false);

    service.openForClaim(CLAIM_ID, TaskType.INITIAL_REVIEW);

    ClaimTask saved = capturedTask();
    assertThat(saved.getStatus()).isEqualTo(ClaimTaskStatus.PENDING_CLAIM.name());
    assertThat(saved.getAssigneeId()).isNull();
    assertThat(saved.getAssignReason()).isEqualTo(AssignReason.ALL_INELIGIBLE.name());
  }

  @Test
  @DisplayName("幂等：已有生效任务时不重复插入（事件重放不得产生第二条任务）")
  void openForClaim_existingTask_isIdempotent() {
    ClaimTask existing =
        ClaimTask.open(
            999L,
            TENANT,
            "T999",
            CLAIM_ID,
            new TaskAssignmentContext(TaskType.INITIAL_REVIEW, "OUTPATIENT", "APP", BigDecimal.ONE),
            100,
            NOW);
    when(taskRepository.findActiveByClaimAndType(CLAIM_ID, TaskType.INITIAL_REVIEW.name()))
        .thenReturn(existing);

    ClaimTaskApplicationService.MyTaskRow row =
        service.openForClaim(CLAIM_ID, TaskType.INITIAL_REVIEW);

    assertThat(row.taskId()).isEqualTo("999");
    verify(taskRepository, never()).insert(any(ClaimTask.class));
  }

  @Test
  @DisplayName("自领乐观锁失败：映射 409 SC_CLAIM_TASK_CONFLICT（不重试、不静默改派）")
  void claim_optimisticLock_mapsToConflict() {
    ClaimTask task = poolTask();
    when(taskRepository.findById(1L)).thenReturn(task);
    when(taskRepository.update(any(ClaimTask.class)))
        .thenThrow(new OptimisticLockingFailureException("t_claim_task", 1L, 0L));

    assertThatThrownBy(() -> service.claim(1L, "u-9"))
        .isInstanceOf(BizException.class)
        .satisfies(
            e -> {
              BizException ex = (BizException) e;
              assertThat(ex.getErrorCode()).isEqualTo(ClaimErrorCodes.SC_CLAIM_TASK_CONFLICT);
              assertThat(ex.getCode()).isEqualTo(409);
            });
  }

  @Test
  @DisplayName("已被他人领取的任务再领：映射 409 而不是 500")
  void claim_alreadyProcessing_mapsToConflict() {
    ClaimTask task = poolTask();
    task.claim("u-1", NOW);
    when(taskRepository.findById(1L)).thenReturn(task);

    assertThatThrownBy(() -> service.claim(1L, "u-2"))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(ClaimErrorCodes.SC_CLAIM_TASK_CONFLICT));
    verify(taskRepository, never()).update(any(ClaimTask.class));
  }

  @Test
  @DisplayName("任务不存在：404 SC_CLAIM_TASK_NOT_FOUND（跨租户按不存在处理，不泄露存在性）")
  void claim_unknownTask_mapsToNotFound() {
    when(taskRepository.findById(anyLong())).thenReturn(null);

    assertThatThrownBy(() -> service.claim(123L, "u-9"))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(ClaimErrorCodes.SC_CLAIM_TASK_NOT_FOUND));
  }

  @Test
  @DisplayName("我的待办 total 与列表同源：find 与 count 共用同一组过滤实参，分页参数只在 find 上")
  void myTasks_totalComesFromSameFilterArgs() {
    ClaimTask task = poolTask();
    when(taskRepository.findMyTasks(eq(TENANT), eq("u-9"), isNull(), anyInt(), anyInt()))
        .thenReturn(List.of(task));
    when(taskRepository.countMyTasks(eq(TENANT), eq("u-9"), isNull())).thenReturn(137L);

    PageResult<ClaimTaskApplicationService.MyTaskRow> page = service.myTasks("u-9", null, 1, 20);

    assertThat(page.getTotal()).isEqualTo(137L);
    assertThat(page.getRecords()).hasSize(1);
    assertThat(page.getRecords().get(0).taskId()).isEqualTo("1");
    // 同源判据：同一 tenant/assignee/status 同时喂给 find 与 count；分页参数只出现在 find 上。
    // 两处实参一旦漂移（例如 count 少一个过滤条件），架构门禁的 paging-same-source 判据会先判红。
    verify(taskRepository).findMyTasks(eq(TENANT), eq("u-9"), isNull(), anyInt(), anyInt());
    verify(taskRepository, times(1)).countMyTasks(eq(TENANT), eq("u-9"), isNull());
  }

  private static ClaimTask poolTask() {
    return ClaimTask.open(
        1L,
        TENANT,
        "T1",
        CLAIM_ID,
        new TaskAssignmentContext(
            TaskType.INITIAL_REVIEW, "OUTPATIENT", "APP", new BigDecimal("1200.00")),
        100,
        NOW);
  }
}
