package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.domain.model.sign.SignBatchItem;
import com.bone.claim.domain.model.sign.event.SignStatusChangedEvent;
import com.bone.claim.domain.model.sign.valueobject.SignCaseOutcome;
import com.bone.claim.domain.model.sign.valueobject.SignStatus;
import com.bone.claim.domain.repository.ClaimSignBatchRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClaimSignCaseCreatorTest {

  @Mock private ClaimSignBatchRepository batchRepository;
  @Mock private ClaimSignCasePipeline pipeline;
  @InjectMocks private ClaimSignCaseCreator creator;

  private SignStatusChangedEvent finishedEvent() {
    return new SignStatusChangedEvent(
        77L,
        10L,
        "SG77",
        SignStatus.WAIT_FOR_CONFIRMATION,
        SignStatus.FINISHED,
        "CONFIRM",
        "u1",
        Instant.now());
  }

  @Test
  @DisplayName("非 FINISHED 事件不触发建案")
  void ignoresNonFinishedEvent() {
    creator.onSignFinished(
        new SignStatusChangedEvent(
            77L,
            10L,
            "SG77",
            SignStatus.WAIT_FOR_PARTICIPANT,
            SignStatus.WAIT_FOR_IMAGE,
            "IMPORT",
            "u1",
            Instant.now()));

    verify(batchRepository, never()).findItemsByBatchId(anyLong());
  }

  @Test
  @DisplayName("建案成功并自动推进初审：轨迹写 CREATED")
  void createdAndAutomated() {
    SignBatchItem item =
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
    when(batchRepository.findItemsByBatchId(77L)).thenReturn(List.of(item));
    when(pipeline.create(10L, 77L, item))
        .thenReturn(new ClaimSignCaseCreation(SignCaseOutcome.NEW, 555L, "CL555", "自动建案成功"));

    creator.onSignFinished(finishedEvent());

    verify(pipeline).allocate(10L, 555L);
    verify(pipeline).startPreExam(10L, 555L);
    verify(batchRepository)
        .saveTrack(
            anyLong(),
            eq(10L),
            eq(77L),
            eq(555L),
            anyString(),
            eq("AUTO_CASE_CREATED"),
            anyString(),
            any(),
            eq("SYSTEM"),
            any());
  }

  @Test
  @DisplayName("分配初审失败：案件已建但不回滚，明细标人工并写失败轨迹")
  void automationFailureMarksManual() {
    SignBatchItem item =
        SignBatchItem.withCaseElements(
            10L,
            77L,
            "CL-TPA-003",
            "pk-tpa-sign-77-CL-TPA-003",
            "P-2026-003",
            0L,
            "王五",
            "110101199001032345",
            Instant.parse("2026-01-04T03:04:05Z"),
            "OUTPATIENT",
            "TPA_PUSH");
    when(batchRepository.findItemsByBatchId(77L)).thenReturn(List.of(item));
    when(pipeline.create(10L, 77L, item))
        .thenReturn(new ClaimSignCaseCreation(SignCaseOutcome.NEW, 556L, "CL556", "自动建案成功"));
    doThrow(new IllegalStateException("规则引擎不可用")).when(pipeline).allocate(10L, 556L);

    creator.onSignFinished(finishedEvent());

    assertThat(item.getCaseStatus()).isEqualTo(SignBatchItem.CASE_FAILED);
    verify(pipeline, never()).startPreExam(anyLong(), anyLong());
    verify(batchRepository).markCaseForItem(anyLong(), eq(10L), eq(SignBatchItem.CASE_FAILED));
    ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
    verify(batchRepository)
        .saveTrack(
            anyLong(),
            eq(10L),
            eq(77L),
            isNull(),
            anyString(),
            eq("AUTO_CASE_FAILED"),
            messageCaptor.capture(),
            any(),
            eq("SYSTEM"),
            any());
    assertThat(messageCaptor.getValue())
        .contains("分配初审")
        .contains("IllegalStateException")
        .contains("规则引擎不可用");
  }

  @Test
  @DisplayName("并发幂等冲突：回写胜出案件并写 SKIPPED 轨迹，不留孤儿报案")
  void duplicateConflictLinksWinner() {
    SignBatchItem item =
        SignBatchItem.withCaseElements(
            10L,
            77L,
            "CL-004",
            "pk-77-CL-004",
            "P-4",
            0L,
            "赵六",
            "110101199001044444",
            Instant.parse("2026-01-05T03:04:05Z"),
            "OUTPATIENT",
            "TPA_PUSH");
    when(batchRepository.findItemsByBatchId(77L)).thenReturn(List.of(item));
    when(pipeline.create(10L, 77L, item)).thenThrow(new SignCaseDuplicateException(999L, "CL999"));

    creator.onSignFinished(finishedEvent());

    assertThat(item.getCaseStatus()).isEqualTo(SignBatchItem.CASE_SKIPPED);
    assertThat(item.getCreatedCaseId()).isEqualTo(999L);
    verify(batchRepository)
        .linkCaseForItem(anyLong(), eq(10L), eq(999L), eq(SignBatchItem.CASE_SKIPPED));
    verify(batchRepository)
        .saveTrack(
            anyLong(),
            eq(10L),
            eq(77L),
            eq(999L),
            anyString(),
            eq("AUTO_CASE_SKIPPED"),
            org.mockito.ArgumentMatchers.contains("CL999"),
            any(),
            eq("SYSTEM"),
            any());
    verify(batchRepository, never())
        .markCaseForItem(anyLong(), eq(10L), eq(SignBatchItem.CASE_FAILED));
  }

  @Test
  @DisplayName("批次明细读取失败：写批次级轨迹且不抛 AFTER_COMMIT 异常")
  void batchReadFailureWritesTrack() {
    when(batchRepository.findItemsByBatchId(77L)).thenThrow(new IllegalStateException("批量查询超时"));

    creator.onSignFinished(finishedEvent());

    verify(batchRepository)
        .saveTrack(
            anyLong(),
            eq(10L),
            eq(77L),
            isNull(),
            anyString(),
            eq("AUTO_CASE_FAILED"),
            org.mockito.ArgumentMatchers.contains("批量查询超时"),
            isNull(),
            eq("SYSTEM"),
            any());
  }
}
