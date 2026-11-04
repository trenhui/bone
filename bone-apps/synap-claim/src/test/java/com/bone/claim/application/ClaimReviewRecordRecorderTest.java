package com.bone.claim.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.bone.claim.domain.model.claim.event.ClaimDecisionMadeEvent;
import com.bone.claim.domain.model.claim.valueobject.Decision;
import com.bone.claim.domain.model.review.ClaimReviewRecord;
import com.bone.claim.domain.repository.ClaimReviewRecordRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 核赔决策留痕记录器测试：订阅 ClaimDecisionMadeEvent 后落库且字段正确。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClaimReviewRecordRecorderTest {

  @Mock private ClaimReviewRecordRepository reviewRecordRepository;

  private ClaimReviewRecordRecorder recorder;

  @BeforeEach
  void setUp() {
    recorder = new ClaimReviewRecordRecorder(reviewRecordRepository);
  }

  @Test
  void recordsReviewOnDecisionMade() {
    ClaimDecisionMadeEvent event =
        new ClaimDecisionMadeEvent(
            11L, 7L, "C1", Decision.APPROVED, "rev-1", "五要素齐全", Instant.now());

    recorder.onClaimDecisionMade(event);

    ArgumentCaptor<ClaimReviewRecord> captor = ArgumentCaptor.forClass(ClaimReviewRecord.class);
    verify(reviewRecordRepository).save(captor.capture());
    ClaimReviewRecord record = captor.getValue();
    assertEquals(7L, record.getTenantId());
    assertEquals(11L, record.getClaimId());
    assertEquals(ClaimReviewRecord.ROLE_APPROVE, record.getRole());
    assertEquals("rev-1", record.getReviewerId());
    assertEquals(ClaimReviewRecord.DECISION_APPROVED, record.getDecision());
    assertEquals("五要素齐全", record.getComment());
  }

  @Test
  void mapsRejectedDecision() {
    ClaimDecisionMadeEvent event =
        new ClaimDecisionMadeEvent(
            11L, 7L, "C1", Decision.REJECTED, "rev-2", "拒赔五要素", Instant.now());

    recorder.onClaimDecisionMade(event);

    ArgumentCaptor<ClaimReviewRecord> captor = ArgumentCaptor.forClass(ClaimReviewRecord.class);
    verify(reviewRecordRepository).save(captor.capture());
    assertEquals(ClaimReviewRecord.DECISION_REJECTED, captor.getValue().getDecision());
  }

  /** 守护契约：审计落库失败必须被吞掉、绝不回滚主流程（合规留痕失败不应阻断业务）。 */
  @Test
  void swallowsRepositoryExceptionSoMainFlowIsNotRolledBack() {
    ClaimDecisionMadeEvent event =
        new ClaimDecisionMadeEvent(
            11L, 7L, "C1", Decision.APPROVED, "rev-1", "五要素齐全", Instant.now());
    doThrow(new RuntimeException("db down")).when(reviewRecordRepository).save(any());

    assertDoesNotThrow(() -> recorder.onClaimDecisionMade(event));
  }
}
