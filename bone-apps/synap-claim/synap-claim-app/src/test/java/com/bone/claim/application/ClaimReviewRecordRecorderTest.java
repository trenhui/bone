package com.bone.claim.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bone.claim.domain.model.claim.event.ClaimDecisionMadeEvent;
import com.bone.claim.domain.model.claim.valueobject.Decision;
import com.bone.claim.domain.model.review.ClaimReviewRecord;
import com.bone.claim.domain.repository.ClaimReviewRecordRepository;
import java.time.Instant;
import java.util.List;
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
    assertEquals(ClaimReviewRecord.ROLE_REVIEW, record.getRole());
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

  /**
   * 枚举完备性守护：遍历 Decision 全部取值，确保每个都成功落库、role 恒为核赔初审 REVIEW、decision 与枚举名一致。 若 {@link Decision} 新增值而
   * recorder 的 toDecisionCode 漏改，default 抛错被兜底吞掉 → save 次数不足 → 本测试红， 强制同步映射（DDL 明确 decision 受控值为
   * APPROVED/REJECTED/REQUEST_MORE_EVIDENCE）。
   */
  @Test
  void mapsAllDecisionEnumValuesAndWritesReviewRole() {
    Decision[] decisions = Decision.values();
    for (Decision d : decisions) {
      recorder.onClaimDecisionMade(
          new ClaimDecisionMadeEvent(11L, 7L, "C1", d, "rev-1", "复核意见", Instant.now()));
    }

    ArgumentCaptor<ClaimReviewRecord> captor = ArgumentCaptor.forClass(ClaimReviewRecord.class);
    verify(reviewRecordRepository, times(decisions.length)).save(captor.capture());
    List<ClaimReviewRecord> saved = captor.getAllValues();
    for (int i = 0; i < decisions.length; i++) {
      assertEquals(
          ClaimReviewRecord.ROLE_REVIEW,
          saved.get(i).getRole(),
          "初审留痕 role 必须为 REVIEW（§11.7 不变量 5）");
      assertEquals(decisions[i].name(), saved.get(i).getDecision());
    }
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
