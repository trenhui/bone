package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bone.claim.domain.model.evidence.event.EvidenceSetLockedEvent;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 链路联动：默认关闭，开启后一次事件只触发一次决策，失败不回滚已提交的锁定。 */
@ExtendWith(MockitoExtension.class)
class DecisionTriggerRecorderTest {

  @Mock private DecisionRunApplicationService decisionRunApplicationService;

  @Test
  @DisplayName("默认关闭：未通过 G3 放量演练前不得自动跑决策图")
  void disabledByDefault() {
    recorder(false).onEvidenceSetLocked(event());

    verifyNoInteractions(decisionRunApplicationService);
  }

  @Test
  @DisplayName("开关打开：证据集锁定事件驱动一次决策执行")
  void enabledTriggersDecision() {
    recorder(true).onEvidenceSetLocked(event());

    verify(decisionRunApplicationService)
        .execute(eq(1001L), any(DecisionGraphExecutor.DecisionVersions.class));
  }

  @Test
  @DisplayName("决策执行抛异常：只告警，不把异常抛回已提交的锁定事务")
  void failureDoesNotBreakLocking() {
    doThrow(new RuntimeException("LLM 网关不可用"))
        .when(decisionRunApplicationService)
        .execute(anyLong(), any());

    assertThatCode(() -> recorder(true).onEvidenceSetLocked(event())).doesNotThrowAnyException();
  }

  private DecisionTriggerRecorder recorder(boolean enabled) {
    DecisionTriggerRecorder recorder = new DecisionTriggerRecorder(decisionRunApplicationService);
    ReflectionTestUtils.setField(recorder, "enabled", enabled);
    return recorder;
  }

  private static EvidenceSetLockedEvent event() {
    return new EvidenceSetLockedEvent(1001L, 42L, 3, "op-1", Instant.now());
  }
}
