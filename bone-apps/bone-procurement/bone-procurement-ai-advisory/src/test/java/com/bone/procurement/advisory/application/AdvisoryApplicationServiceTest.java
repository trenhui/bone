package com.bone.procurement.advisory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.procurement.advisory.domain.model.advisory.AdvisoryResponse;
import com.bone.procurement.advisory.domain.model.advisory.AiAvailability;
import com.bone.procurement.governance.application.AiGovernanceApplicationService;
import com.bone.procurement.governance.application.SafetyApplicationService;
import com.bone.procurement.governance.domain.model.capability.CapabilityCode;
import com.bone.procurement.governance.domain.model.capability.CapabilityState;
import com.bone.procurement.governance.domain.model.capability.GovernanceDecision;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 应用服务：总闸关闭 / 能力未登记 / 裁决拒绝一律 UNKNOWN + rules_only，绝不 fail-open。 */
class AdvisoryApplicationServiceTest {

  private AiGovernanceApplicationService governance;
  private SafetyApplicationService safety;
  private AdvisoryApplicationService service;

  @BeforeEach
  void setUp() {
    governance = mock(AiGovernanceApplicationService.class);
    safety = mock(SafetyApplicationService.class);
    service = new AdvisoryApplicationService(governance, safety);
  }

  @Test
  @DisplayName("全域 kill switch 关闭时一律 UNKNOWN + rules_only")
  void killSwitchBlocksAi() {
    when(safety.globalAllowsAi()).thenReturn(false);
    AdvisoryResponse r = service.advise("M1");
    assertThat(r.aiAvailable()).isEqualTo(AiAvailability.UNKNOWN);
    assertThat(r.degradedTo()).isEqualTo("rules_only");
    assertThat(r.reasonCode()).isEqualTo("GLOBAL_KILL_SWITCH_ENGAGED");
    assertThat(r.advisoryOnly()).isTrue();
  }

  @Test
  @DisplayName("能力裁决拒绝时降为 rules_only，不进入 AVAILABLE")
  void governanceDenyFallsBack() {
    when(safety.globalAllowsAi()).thenReturn(true);
    GovernanceDecision deny =
        GovernanceDecision.deny(
            new CapabilityCode("tender-health-check"),
            CapabilityState.DISABLED,
            GovernanceDecision.REASON_DISABLED);
    when(governance.decide("tender-health-check")).thenReturn(deny);
    AdvisoryResponse r = service.advise("M1");
    assertThat(r.aiAvailable()).isEqualTo(AiAvailability.UNKNOWN);
    assertThat(r.reasonCode()).isEqualTo(GovernanceDecision.REASON_DISABLED);
    assertThat(r.degradedTo()).isEqualTo("rules_only");
  }

  @Test
  @DisplayName("裁决放行时 AVAILABLE 且双标识齐全、AI 仍仅建议")
  void governanceAllowYieldsAdvisory() {
    when(safety.globalAllowsAi()).thenReturn(true);
    GovernanceDecision allow = GovernanceDecision.allow(new CapabilityCode("contract-drafting"));
    when(governance.decide("contract-drafting")).thenReturn(allow);
    AdvisoryResponse r = service.advise("M7");
    assertThat(r.aiAvailable()).isEqualTo(AiAvailability.AVAILABLE);
    assertThat(r.advisoryOnly()).isTrue();
    assertThat(r.hasDualLabels()).isTrue();
    assertThat(r.canRelease()).isTrue();
    assertThat(r.decidedAt()).isBeforeOrEqualTo(Instant.now());
  }

  @Test
  @DisplayName("未知能力码 fail-closed")
  void unknownCapability() {
    AdvisoryResponse r = service.advise("NOT_A_CODE");
    assertThat(r.aiAvailable()).isEqualTo(AiAvailability.UNKNOWN);
    assertThat(r.reasonCode()).isEqualTo("UNKNOWN_CAPABILITY");
    assertThat(r.capabilityCode()).isEqualTo("UNKNOWN");
  }
}
