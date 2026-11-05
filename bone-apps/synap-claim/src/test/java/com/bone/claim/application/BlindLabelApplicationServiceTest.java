package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.decision.AiConfidenceSource;
import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.claim.domain.model.decision.DecisionResult;
import com.bone.claim.domain.model.decision.DecisionTier;
import com.bone.claim.domain.model.evaluation.BlindLabelTask;
import com.bone.claim.domain.model.evaluation.BlindLabelUnblindedEvent;
import com.bone.claim.domain.model.inspection.InspectionRecord;
import com.bone.claim.domain.repository.BlindLabelTaskRepository;
import com.bone.claim.domain.repository.DecisionExecutionRepository;
import com.bone.claim.domain.repository.InspectionRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 独立盲标应用服务测试（G4-1 四道防反馈环闸）。
 *
 * <p>判据：派发幂等（同案未解盲唯一）、跨租户按不存在处理、载荷禁区 422、标注人去污染 409、 AI 参与口径只认 {@code confidence_source ==
 * MODEL}（桩/降级不算）、解盲配对发布事件。
 */
class BlindLabelApplicationServiceTest {

  private static final Long TENANT = 42L;
  private static final Long CLAIM = 9001L;
  private static final String PAYLOAD = "{\"claimNo\":\"C-1\",\"evidenceIds\":[1,2]}";
  private static final String RESULT = "{\"decision\":\"APPROVE\",\"amount\":100}";

  private BlindLabelTaskRepository blindLabelTaskRepository;
  private ClaimCaseApplicationService claimCaseApplicationService;
  private InspectionRepository inspectionRepository;
  private DecisionExecutionRepository decisionExecutionRepository;
  private DomainEventPublisher domainEventPublisher;
  private BlindLabelApplicationService service;

  @BeforeEach
  void setUp() {
    blindLabelTaskRepository = mock(BlindLabelTaskRepository.class);
    claimCaseApplicationService = mock(ClaimCaseApplicationService.class);
    inspectionRepository = mock(InspectionRepository.class);
    decisionExecutionRepository = mock(DecisionExecutionRepository.class);
    domainEventPublisher = mock(DomainEventPublisher.class);
    service =
        new BlindLabelApplicationService(
            blindLabelTaskRepository,
            claimCaseApplicationService,
            inspectionRepository,
            decisionExecutionRepository,
            () -> TENANT,
            domainEventPublisher,
            new ObjectMapper());
  }

  @Test
  @DisplayName("派发：字段落位、指纹 64hex、任务号 BL 前缀、无事件（未到解盲时点）")
  void dispatchHappy() {
    ClaimCase claim = claim();
    when(claimCaseApplicationService.getClaimAggregate(CLAIM)).thenReturn(claim);
    when(blindLabelTaskRepository.findActiveByClaimId(CLAIM)).thenReturn(null);
    when(inspectionRepository.findLatestByClaimId(CLAIM)).thenReturn(null);

    BlindLabelApplicationService.BlindLabelView view =
        service.dispatch(CLAIM, "annotator-1", PAYLOAD);

    assertThat(view.status()).isEqualTo(BlindLabelTask.Status.PENDING.name());
    assertThat(view.taskNo()).startsWith("BL");
    assertThat(view.payloadHash()).matches("^[0-9a-f]{64}$");
    assertThat(view.aiSuggestionJson()).isNull();
    verify(blindLabelTaskRepository).insert(any(BlindLabelTask.class));
    verify(domainEventPublisher, never()).publishFrom(any(BlindLabelTask.class));
  }

  @Test
  @DisplayName("派发：同案已有未解盲任务 → 409（两个标注人并行盲标无法归因）")
  void dispatchRejectsDuplicateActiveTask() {
    ClaimCase claim = claim();
    when(claimCaseApplicationService.getClaimAggregate(CLAIM)).thenReturn(claim);
    when(blindLabelTaskRepository.findActiveByClaimId(CLAIM)).thenReturn(activeTask());

    BizException ex =
        catchThrowableOfType(
            BizException.class, () -> service.dispatch(CLAIM, "annotator-1", PAYLOAD));
    assertThat(ex.getErrorCode()).isEqualTo(ClaimErrorCodes.SC_CLAIM_BLIND_LABEL_CONFLICT);
  }

  @Test
  @DisplayName("派发：案件不存在 / 跨租户一律按不存在处理（不泄露存在性）")
  void dispatchClaimNotFoundOrCrossTenant() {
    when(claimCaseApplicationService.getClaimAggregate(CLAIM)).thenReturn(null);
    assertThat(
            catchThrowableOfType(BizException.class, () -> service.dispatch(CLAIM, "a", PAYLOAD))
                .getErrorCode())
        .isEqualTo(ClaimErrorCodes.CLAIM_NOT_FOUND);

    ClaimCase other = claim();
    other.setTenantId(TENANT + 1);
    when(claimCaseApplicationService.getClaimAggregate(CLAIM)).thenReturn(other);
    assertThat(
            catchThrowableOfType(BizException.class, () -> service.dispatch(CLAIM, "a", PAYLOAD))
                .getErrorCode())
        .isEqualTo(ClaimErrorCodes.CLAIM_NOT_FOUND);
  }

  @Test
  @DisplayName("载荷禁区：含 AI 痕迹键的载荷 422——闸门被绕过时显式失败而非静默放行")
  void dispatchRejectsForbiddenPayloadKey() {
    ClaimCase claim = claim();
    when(claimCaseApplicationService.getClaimAggregate(CLAIM)).thenReturn(claim);
    when(blindLabelTaskRepository.findActiveByClaimId(CLAIM)).thenReturn(null);
    when(inspectionRepository.findLatestByClaimId(CLAIM)).thenReturn(null);

    String leak = "{\"claimNo\":\"C-1\",\"aiConfidence\":0.93}";
    BizException ex =
        catchThrowableOfType(BizException.class, () -> service.dispatch(CLAIM, "a", leak));
    assertThat(ex.getErrorCode()).isEqualTo(ClaimErrorCodes.SC_CLAIM_BLIND_LABEL_PAYLOAD_FORBIDDEN);
    verify(blindLabelTaskRepository, never()).insert(any(BlindLabelTask.class));
  }

  @Test
  @DisplayName("载荷必须是 JSON 对象：数组 / 非法 JSON / 空对象都拒绝")
  void dispatchRejectsNonObjectPayload() {
    ClaimCase claim = claim();
    when(claimCaseApplicationService.getClaimAggregate(CLAIM)).thenReturn(claim);
    when(blindLabelTaskRepository.findActiveByClaimId(CLAIM)).thenReturn(null);
    when(inspectionRepository.findLatestByClaimId(CLAIM)).thenReturn(null);

    for (String bad : List.of("[1,2]", "not-json", "{}")) {
      assertThat(
              catchThrowableOfType(BizException.class, () -> service.dispatch(CLAIM, "a", bad))
                  .getErrorCode())
          .isEqualTo(ClaimErrorCodes.SC_CLAIM_BLIND_LABEL_PAYLOAD_FORBIDDEN);
    }
  }

  @Test
  @DisplayName("标注人去污染：与本案抽检质检人同人 → 409（先看过 AI 建议再盲标=被污染）")
  void dispatchRejectsPollutedAnnotator() {
    ClaimCase claim = claim();
    when(claimCaseApplicationService.getClaimAggregate(CLAIM)).thenReturn(claim);
    when(blindLabelTaskRepository.findActiveByClaimId(CLAIM)).thenReturn(null);
    InspectionRecord inspection = mock(InspectionRecord.class);
    when(inspection.getInspectorId()).thenReturn("annotator-1");
    when(inspection.getInspectionNo()).thenReturn("INS-1");
    when(inspectionRepository.findLatestByClaimId(CLAIM)).thenReturn(inspection);

    BizException ex =
        catchThrowableOfType(
            BizException.class, () -> service.dispatch(CLAIM, "annotator-1", PAYLOAD));
    assertThat(ex.getErrorCode())
        .isEqualTo(ClaimErrorCodes.SC_CLAIM_BLIND_LABEL_ANNOTATOR_CONFLICT);
  }

  @Test
  @DisplayName("提交：PENDING → LABELED；任务不存在 404")
  void submitHappyAndNotFound() {
    BlindLabelTask task = activeTask();
    when(blindLabelTaskRepository.findById(1L)).thenReturn(task);

    BlindLabelApplicationService.BlindLabelView view = service.submit(1L, RESULT);
    assertThat(view.status()).isEqualTo(BlindLabelTask.Status.LABELED.name());
    assertThat(view.labelResultJson()).isEqualTo(RESULT);
    verify(blindLabelTaskRepository).save(task);

    when(blindLabelTaskRepository.findById(2L)).thenReturn(null);
    assertThat(
            catchThrowableOfType(BizException.class, () -> service.submit(2L, RESULT))
                .getErrorCode())
        .isEqualTo(ClaimErrorCodes.SC_CLAIM_BLIND_LABEL_NOT_FOUND);
  }

  @Test
  @DisplayName("解盲：confidence_source=MODEL 才算 AI 参与 → PAIRED + 快照落位 + 事件发布")
  void unblindAiParticipated() {
    BlindLabelTask task = activeTask();
    task.submit(RESULT, java.time.Instant.now());
    when(blindLabelTaskRepository.findById(1L)).thenReturn(task);
    DecisionExecution execution = execution(AiConfidenceSource.MODEL);
    when(decisionExecutionRepository.findLatestByClaimId(CLAIM)).thenReturn(execution);

    BlindLabelApplicationService.BlindLabelView view = service.unblind(1L);

    assertThat(view.agreement()).isEqualTo(BlindLabelTask.Agreement.PAIRED.name());
    assertThat(view.aiSuggestionJson()).contains("\"decisionResult\"");
    assertThat(view.aiParticipated()).isTrue();
    verify(blindLabelTaskRepository).save(task);
    verify(domainEventPublisher).publishFrom(task);
    assertThat(task.getDomainEvents()).hasSize(1);
    BlindLabelUnblindedEvent event = (BlindLabelUnblindedEvent) task.getDomainEvents().get(0);
    assertThat(event.aiParticipated()).isTrue();
  }

  @Test
  @DisplayName("解盲：无执行或桩/降级来源 → AI_ABSENT，AI 侧快照为空（假证据不冒充）")
  void unblindAiAbsentWhenStubOrFallback() {
    for (AiConfidenceSource source :
        new AiConfidenceSource[] {AiConfidenceSource.STUB, AiConfidenceSource.FALLBACK}) {
      BlindLabelTask task = activeTask();
      task.submit(RESULT, java.time.Instant.now());
      when(blindLabelTaskRepository.findById(1L)).thenReturn(task);
      when(decisionExecutionRepository.findLatestByClaimId(CLAIM)).thenReturn(execution(source));

      BlindLabelApplicationService.BlindLabelView view = service.unblind(1L);
      assertThat(view.agreement()).isEqualTo(BlindLabelTask.Agreement.AI_ABSENT.name());
      assertThat(view.aiSuggestionJson()).isNull();
    }

    BlindLabelTask task = activeTask();
    task.submit(RESULT, java.time.Instant.now());
    when(blindLabelTaskRepository.findById(1L)).thenReturn(task);
    when(decisionExecutionRepository.findLatestByClaimId(CLAIM)).thenReturn(null);
    BlindLabelApplicationService.BlindLabelView view = service.unblind(1L);
    assertThat(view.agreement()).isEqualTo(BlindLabelTask.Agreement.AI_ABSENT.name());
  }

  @Test
  @DisplayName("读单防御：未解盲视图不含 AI 侧字段")
  void viewHidesAiFieldsBeforeUnblind() {
    BlindLabelTask task = activeTask();
    when(blindLabelTaskRepository.findById(1L)).thenReturn(task);

    BlindLabelApplicationService.BlindLabelView view = service.get(1L);
    assertThat(view.aiSuggestionJson()).isNull();
    assertThat(view.agreement()).isNull();
  }

  // ===================== 夹具 =====================

  private static ClaimCase claim() {
    // ClaimCase 是 Entity 系聚合：getId() 经反射读字段，Mockito 默认 mock maker 无法桩 getId
    // （会抛 UnfinishedStubbing），故直接构造真实实例并设置 id/tenantId。
    try {
      Constructor<ClaimCase> ctor = ClaimCase.class.getDeclaredConstructor();
      ctor.setAccessible(true);
      ClaimCase claim = ctor.newInstance();
      claim.setId(CLAIM);
      claim.setTenantId(TENANT);
      return claim;
    } catch (Exception e) {
      throw new IllegalStateException("构造 ClaimCase 失败", e);
    }
  }

  private static BlindLabelTask activeTask() {
    return BlindLabelTask.open(1L, TENANT, "BL1", CLAIM, "annotator-1", PAYLOAD, "a".repeat(64));
  }

  private static DecisionExecution execution(AiConfidenceSource source) {
    DecisionExecution execution =
        DecisionExecution.start(
            9001L, TENANT, "t-1", CLAIM, "graph-v1", "rule-v1", "model-v1", "prompt-v1", "cat-v1");
    execution.complete(
        DecisionResult.MANUAL_REQUIRED,
        DecisionTier.MANUAL,
        new BigDecimal("0.9300"),
        false,
        source);
    return execution;
  }
}
