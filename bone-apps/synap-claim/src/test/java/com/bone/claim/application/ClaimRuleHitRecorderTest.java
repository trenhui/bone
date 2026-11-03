package com.bone.claim.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bone.claim.domain.model.rule.ClaimRuleHit;
import com.bone.claim.domain.model.rule.event.ClaimRulesEvaluatedEvent;
import com.bone.claim.domain.repository.ClaimRuleHitRepository;
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

/** 规则命中留痕记录器测试：订阅 ClaimRulesEvaluatedEvent 后逐条落库；无命中则跳过。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClaimRuleHitRecorderTest {

  @Mock private ClaimRuleHitRepository ruleHitRepository;

  private ClaimRuleHitRecorder recorder;

  @BeforeEach
  void setUp() {
    recorder = new ClaimRuleHitRecorder(ruleHitRepository);
  }

  @Test
  void recordsEachHitOnEvaluated() {
    Instant now = Instant.now();
    List<ClaimRulesEvaluatedEvent.RuleHit> hits =
        List.of(
            new ClaimRulesEvaluatedEvent.RuleHit(
                "R-AREA-01", "v1", ClaimRuleHit.CATEGORY_AREA, 1, "{}", "HIT", now),
            new ClaimRulesEvaluatedEvent.RuleHit(
                "R-CAP-02", "v1", ClaimRuleHit.CATEGORY_CAP, 2, "{}", "PASS", now));
    ClaimRulesEvaluatedEvent event = new ClaimRulesEvaluatedEvent(7L, 11L, 99L, hits, now);

    recorder.onRulesEvaluated(event);

    ArgumentCaptor<ClaimRuleHit> captor = ArgumentCaptor.forClass(ClaimRuleHit.class);
    verify(ruleHitRepository, times(2)).save(captor.capture());
    List<ClaimRuleHit> saved = captor.getAllValues();
    assertEquals(99L, saved.get(0).getExecutionId());
    assertEquals(11L, saved.get(0).getClaimId());
    assertEquals(ClaimRuleHit.CATEGORY_AREA, saved.get(0).getRuleCategory());
    assertEquals(ClaimRuleHit.CATEGORY_CAP, saved.get(1).getRuleCategory());
  }

  @Test
  void recordsNothingWhenNoHits() {
    ClaimRulesEvaluatedEvent event =
        new ClaimRulesEvaluatedEvent(7L, 11L, 99L, List.of(), Instant.now());

    recorder.onRulesEvaluated(event);

    verify(ruleHitRepository, never()).save(any());
  }
}
