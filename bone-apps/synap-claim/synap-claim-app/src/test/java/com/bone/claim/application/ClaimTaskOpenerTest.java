package com.bone.claim.application;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bone.claim.domain.model.claim.event.ClaimStatusChangedEvent;
import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import com.bone.claim.domain.model.task.TaskType;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 自动签收链路停在 AUTO_PRE_EXAMINING，初审任务必须在这一状态就建立。 */
@ExtendWith(MockitoExtension.class)
class ClaimTaskOpenerTest {

  @Mock private ClaimTaskApplicationService tasks;
  @InjectMocks private ClaimTaskOpener opener;

  @Test
  @DisplayName("自动初审状态触发 INITIAL_REVIEW 任务")
  void autoPreExamOpensTask() {
    opener.onClaimStatusChanged(event(ClaimStatus.AUTO_PRE_EXAMINING));
    verify(tasks).openForClaim(4711L, TaskType.INITIAL_REVIEW);
  }

  @Test
  @DisplayName("待核赔状态仍触发 INITIAL_REVIEW 任务（幂等由应用服务保证）")
  void pendingAdjustStillOpensTask() {
    opener.onClaimStatusChanged(event(ClaimStatus.PENDING_ADJUST));
    verify(tasks).openForClaim(4711L, TaskType.INITIAL_REVIEW);
  }

  @Test
  @DisplayName("其他状态不开任务")
  void otherStatusIgnored() {
    opener.onClaimStatusChanged(event(ClaimStatus.ACCEPTED));
    verify(tasks, never())
        .openForClaim(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
  }

  private static ClaimStatusChangedEvent event(ClaimStatus to) {
    return new ClaimStatusChangedEvent(
        4711L, 10L, "CL4711", ClaimStatus.ACCEPTED, to, "TEST", "SYSTEM", Instant.now());
  }
}
