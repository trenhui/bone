package com.bone.claim.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.bone.claim.domain.model.claim.ClaimCaseTrack;
import com.bone.claim.domain.model.claim.event.ClaimStatusChangedEvent;
import com.bone.claim.domain.repository.ClaimCaseTrackRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 状态流转流水记录器测试：订阅 ClaimStatusChangedEvent 后落库且字段正确。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClaimCaseTrackRecorderTest {

  @Mock private ClaimCaseTrackRepository trackRepository;

  private ClaimCaseTrackRecorder recorder;

  @BeforeEach
  void setUp() {
    recorder = new ClaimCaseTrackRecorder(trackRepository);
  }

  @Test
  void recordsTrackOnStatusChanged() {
    ClaimStatusChangedEvent event =
        new ClaimStatusChangedEvent(
            11L, 7L, "C1", "DRAFT", "SUBMITTING", "SUBMIT", "op1", Instant.now());

    recorder.onClaimStatusChanged(event);

    ArgumentCaptor<ClaimCaseTrack> captor = ArgumentCaptor.forClass(ClaimCaseTrack.class);
    verify(trackRepository).save(captor.capture());
    ClaimCaseTrack track = captor.getValue();
    assertEquals(7L, track.getTenantId());
    assertEquals(11L, track.getClaimId());
    assertEquals("DRAFT", track.getFromStatus());
    assertEquals("SUBMITTING", track.getToStatus());
    assertEquals("SUBMIT", track.getStage());
  }
}
