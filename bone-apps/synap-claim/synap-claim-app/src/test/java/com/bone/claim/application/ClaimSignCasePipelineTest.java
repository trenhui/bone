package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.ClaimIntakePort;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.ClaimReport;
import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import com.bone.claim.domain.model.sign.SignBatchItem;
import com.bone.claim.domain.model.sign.valueobject.SignCaseOutcome;
import com.bone.claim.domain.model.stakeholder.ClaimStakeholder;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.ClaimSignBatchRepository;
import com.bone.claim.domain.repository.ClaimStakeholderRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClaimSignCasePipelineTest {

  @Mock private ClaimCaseRepository caseRepository;
  @Mock private ClaimIntakePort intakePort;
  @Mock private ClaimSignBatchRepository batchRepository;
  @Mock private ClaimStakeholderRepository stakeholderRepository;
  @Mock private DomainEventPublisher publisher;
  @InjectMocks private ClaimSignCasePipeline pipeline;

  private SignBatchItem item;

  @BeforeEach
  void setUp() {
    item =
        SignBatchItem.withCaseElements(
            10L,
            77L,
            "CL-TPA-001",
            "pk-tpa-sign-77-CL-TPA-001",
            "P-2026-001",
            0L,
            "张三",
            "110101199001011234",
            Instant.parse("2026-01-02T03:04:05Z"),
            "OUTPATIENT",
            "TPA_PUSH");
  }

  @Test
  @DisplayName("同 business_key 已有案件：跳过建案并回写关联，不产生第二份报案")
  void duplicateEventDoesNotCreateSecondCase() {
    ClaimCase existing = org.mockito.Mockito.mock(ClaimCase.class);
    when(existing.getId()).thenReturn(555L);
    when(existing.getClaimNo()).thenReturn("CL555");
    when(caseRepository.findByIdempotencyKey(10L, item.getBusinessKey())).thenReturn(existing);

    ClaimSignCaseCreation result = pipeline.create(10L, 77L, item);

    assertThat(result.outcome()).isEqualTo(SignCaseOutcome.SKIPPED);
    assertThat(result.claimId()).isEqualTo(555L);
    verify(intakePort, never()).persistIntake(any(), any());
    verify(batchRepository).linkCaseForItem(anyLong(), anyLong(), anyLong(), any());
  }

  @Test
  @DisplayName("要素齐全：报案实名明文落库，赔案建案即受理，并回写 created_case_id")
  void createCaseWithRealNameElements() {
    when(caseRepository.findByIdempotencyKey(10L, item.getBusinessKey())).thenReturn(null);

    ClaimSignCaseCreation result = pipeline.create(10L, 77L, item);

    assertThat(result.outcome()).isEqualTo(SignCaseOutcome.NEW);
    ArgumentCaptor<ClaimReport> reportCaptor = ArgumentCaptor.forClass(ClaimReport.class);
    ArgumentCaptor<ClaimCase> caseCaptor = ArgumentCaptor.forClass(ClaimCase.class);
    verify(intakePort).persistIntake(reportCaptor.capture(), caseCaptor.capture());
    assertThat(reportCaptor.getValue().getInsuredIdHash()).isEqualTo("110101199001011234");
    assertThat(reportCaptor.getValue().getPolicyNo()).isEqualTo("P-2026-001");
    assertThat(caseCaptor.getValue().status()).isEqualTo(ClaimStatus.ACCEPTED);
    assertThat(caseCaptor.getValue().getItems()).hasSize(1);
    ArgumentCaptor<ClaimStakeholder> stakeholderCaptor =
        ArgumentCaptor.forClass(ClaimStakeholder.class);
    verify(stakeholderRepository).save(stakeholderCaptor.capture());
    assertThat(stakeholderCaptor.getValue().getNameMasked()).isEqualTo("张三");
    assertThat(stakeholderCaptor.getValue().getPersonIdHash()).isEqualTo("110101199001011234");
    verify(batchRepository).linkCaseForItem(anyLong(), anyLong(), anyLong(), any());
    verify(publisher).publishFrom(caseCaptor.getValue());
  }

  @Test
  @DisplayName("要素缺失：不建案，明细落到待人工态")
  void missingElementsGoesToManual() {
    SignBatchItem incomplete =
        SignBatchItem.withCaseElements(
            10L,
            77L,
            "CL-TPA-002",
            "pk-tpa-sign-77-CL-TPA-002",
            "",
            0L,
            "李四",
            "110101199001015678",
            Instant.parse("2026-01-03T03:04:05Z"),
            "OUTPATIENT",
            "TPA_PUSH");

    ClaimSignCaseCreation result = pipeline.create(10L, 77L, incomplete);

    assertThat(result.outcome()).isEqualTo(SignCaseOutcome.AWAIT_MANUAL);
    verify(intakePort, never()).persistIntake(any(), any());
    verify(batchRepository).markCaseForItem(anyLong(), anyLong(), any());
  }

  @Test
  @DisplayName("并发撞幂等键：抛冲突异常让事务回滚，不在当前事务写明细关联")
  void duplicateRaceThrowsAndRollsBack() {
    ClaimCase winner = org.mockito.Mockito.mock(ClaimCase.class);
    when(winner.getId()).thenReturn(888L);
    when(winner.getClaimNo()).thenReturn("CL888");
    // 首次幂等检查为空，写入撞唯一键后再次查询才拿到并发胜出者。
    when(caseRepository.findByIdempotencyKey(10L, item.getBusinessKey())).thenReturn(null, winner);
    doThrow(new DataIntegrityViolationException("duplicate key"))
        .when(intakePort)
        .persistIntake(any(), any());

    assertThatThrownBy(() -> pipeline.create(10L, 77L, item))
        .isInstanceOf(SignCaseDuplicateException.class)
        .hasMessageContaining("888");
    verify(batchRepository, never()).linkCaseForItem(anyLong(), anyLong(), anyLong(), any());
    verify(stakeholderRepository, never()).save(any());
  }

  @Test
  @DisplayName("实名姓名缺失：不建案，转人工补录而不是写空名")
  void missingInsuredNameGoesToManual() {
    SignBatchItem missingName =
        SignBatchItem.withCaseElements(
            10L,
            77L,
            "CL-005",
            "pk-77-CL-005",
            "P-5",
            0L,
            "",
            "110101199001055555",
            Instant.parse("2026-01-06T03:04:05Z"),
            "OUTPATIENT",
            "TPA_PUSH");

    assertThat(missingName.needsCaseCreation()).isFalse();
    assertThat(pipeline.create(10L, 77L, missingName).outcome())
        .isEqualTo(SignCaseOutcome.AWAIT_MANUAL);
    verify(intakePort, never()).persistIntake(any(), any());
  }
}
