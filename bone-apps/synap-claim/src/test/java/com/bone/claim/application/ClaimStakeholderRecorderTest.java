package com.bone.claim.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.event.ClaimSubmittedEvent;
import com.bone.claim.domain.model.stakeholder.ClaimStakeholder;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.ClaimStakeholderRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 报案干系人留痕记录器测试：订阅 ClaimSubmittedEvent 后落库被保人行；缺锚点则跳过。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClaimStakeholderRecorderTest {

  @Mock private ClaimCaseRepository claimCaseRepository;
  @Mock private ClaimStakeholderRepository stakeholderRepository;

  private ClaimStakeholderRecorder recorder;

  @BeforeEach
  void setUp() {
    recorder = new ClaimStakeholderRecorder(claimCaseRepository, stakeholderRepository);
  }

  @Test
  void recordsInsuredOnSubmitted() {
    ClaimSubmittedEvent event = new ClaimSubmittedEvent(11L, 7L, "C1", Instant.now());
    ClaimCase claim = mock(ClaimCase.class);
    when(claim.getInsuredIdHash()).thenReturn("hash-abc");
    when(claimCaseRepository.findByClaimNo(7L, "C1")).thenReturn(claim);

    recorder.onClaimSubmitted(event);

    ArgumentCaptor<ClaimStakeholder> captor = ArgumentCaptor.forClass(ClaimStakeholder.class);
    verify(stakeholderRepository).save(captor.capture());
    ClaimStakeholder s = captor.getValue();
    assertEquals(7L, s.getTenantId());
    assertEquals(11L, s.getClaimId());
    assertEquals(ClaimStakeholder.ROLE_INSURED, s.getRole());
    assertEquals("hash-abc", s.getPersonIdHash());
  }

  @Test
  void skipsWhenNoInsuredHash() {
    ClaimSubmittedEvent event = new ClaimSubmittedEvent(11L, 7L, "C1", Instant.now());
    ClaimCase claim = mock(ClaimCase.class);
    when(claim.getInsuredIdHash()).thenReturn(null);
    when(claimCaseRepository.findByClaimNo(7L, "C1")).thenReturn(claim);

    recorder.onClaimSubmitted(event);

    verify(stakeholderRepository, never()).save(any());
  }

  /** 守护契约：审计落库失败必须被吞掉、绝不回滚主流程（合规留痕失败不应阻断业务）。 */
  @Test
  void swallowsRepositoryExceptionSoMainFlowIsNotRolledBack() {
    ClaimSubmittedEvent event = new ClaimSubmittedEvent(11L, 7L, "C1", Instant.now());
    ClaimCase claim = mock(ClaimCase.class);
    when(claim.getInsuredIdHash()).thenReturn("hash-abc");
    when(claimCaseRepository.findByClaimNo(7L, "C1")).thenReturn(claim);
    doThrow(new RuntimeException("db down")).when(stakeholderRepository).save(any());

    assertDoesNotThrow(() -> recorder.onClaimSubmitted(event));
  }
}
