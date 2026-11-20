package com.bone.procurement.governance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.procurement.governance.domain.gateway.CapabilityRegistry;
import com.bone.procurement.governance.domain.gateway.GlobalKillSwitchStore;
import com.bone.procurement.governance.domain.gateway.GovernanceActorPort;
import com.bone.procurement.governance.domain.gateway.GovernanceTracePort;
import com.bone.procurement.governance.domain.model.capability.AiCapability;
import com.bone.procurement.governance.domain.model.capability.CapabilityCode;
import com.bone.procurement.governance.domain.model.capability.OwnerRef;
import com.bone.procurement.governance.domain.model.fairness.BiasSignal;
import com.bone.procurement.governance.domain.model.fairness.FairnessDecision;
import com.bone.procurement.governance.domain.model.labeling.ContentLabel;
import com.bone.procurement.governance.domain.model.labeling.DeliveryDecision;
import com.bone.procurement.governance.infrastructure.InMemoryCapabilityRegistry;
import com.bone.procurement.governance.infrastructure.InMemoryGlobalKillSwitchStore;
import com.bone.procurement.governance.infrastructure.InMemoryGovernanceTracePort;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 批次3 应用服务测试：kill switch 读库即时生效 + 双标识拦截 + 公平裁决 + 演练状态。 */
class SafetyApplicationServiceTest {

  private GlobalKillSwitchStore ksStore;
  private CapabilityRegistry registry;
  private GovernanceTracePort tracePort;
  private StubActor actor;
  private SafetyApplicationService service;

  @BeforeEach
  void setUp() {
    ksStore = new InMemoryGlobalKillSwitchStore();
    registry = new InMemoryCapabilityRegistry();
    tracePort = new InMemoryGovernanceTracePort();
    actor = new StubActor("admin-1");
    service = new SafetyApplicationService(ksStore, registry, tracePort, actor);
  }

  @Test
  void 缺省killSwitch不放行AI() {
    assertThat(service.currentKillSwitch().mode()).isEqualTo("STANDBY");
    assertThat(service.globalAllowsAi()).isFalse();
  }

  @Test
  void 一键停用后读库即时不允许AI() {
    service.engageKillSwitch("应急", "DRILL-1");
    assertThat(service.globalAllowsAi()).isFalse();
    assertThat(service.currentKillSwitch().drillRef()).isEqualTo("DRILL-1");
  }

  @Test
  void 显式解除后才允许AI() {
    service.engageKillSwitch("停机", "D-1");
    service.disengageKillSwitch("恢复");
    assertThat(service.globalAllowsAi()).isTrue();
    assertThat(service.currentKillSwitch().mode()).isEqualTo("DISENGAGED");
  }

  @Test
  void 匿名killSwitch操作failClosed() {
    actor.id = null;
    assertThatThrownBy(() -> service.engageKillSwitch("x", null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("登录态");
  }

  @Test
  void 停机演练显示所有AI路径失活() {
    AiCapability cap =
        AiCapability.register(new CapabilityCode("M2"), "合同审查", OwnerRef.of("u", "张三"));
    cap.enable("admin", "启用");
    registry.save(cap);
    // 未解除全局开关 → 即使能力 AVAILABLE，AI 路径也不激活。
    List<KillSwitchView.CapabilityEffect> effects = service.drillStatus();
    assertThat(effects).hasSize(1);
    assertThat(effects.get(0).state()).isEqualTo("AVAILABLE");
    assertThat(effects.get(0).aiPathActive()).isFalse();
    // 解除后激活。
    service.engageKillSwitch("停机", "D-1");
    service.disengageKillSwitch("恢复");
    assertThat(service.drillStatus().get(0).aiPathActive()).isTrue();
  }

  @Test
  void LABEL01缺隐式标识阻断出域并留痕() {
    DeliveryDecision d =
        service.checkDelivery(new ContentLabel("report://M2/1", "M2", "AI辅助生成", "", true));
    assertThat(d.released()).isFalse();
    assertThat(d.reasonCode()).isEqualTo(DeliveryDecision.REASON_MISSING_IMPLICIT);
    assertThat(tracePort.listByCapability("M2"))
        .extracting(e -> e.eventType())
        .contains("LABEL_BLOCKED");
  }

  @Test
  void 双标识齐备放行出域() {
    DeliveryDecision d =
        service.checkDelivery(
            new ContentLabel("draft://M7/1", "M7", "AI辅助起草", "ai_generated=true", true));
    assertThat(d.released()).isTrue();
  }

  @Test
  void FAIR01所有制偏见阻断模型晋升() {
    BiasSignal s =
        new BiasSignal(
            "S-1",
            BiasSignal.OwnershipType.PRIVATE,
            BiasSignal.RegionTier.EAST,
            BiasSignal.ScaleTier.MEDIUM,
            "国企优先",
            0.9);
    FairnessDecision d = service.evaluateFairness(s);
    assertThat(d.decision()).isEqualTo("REJECT_RELEASE");
    assertThat(d.modelVersionPromoted()).isFalse();
    assertThat(d.ethicsReviewRequired()).isTrue();
  }

  @Test
  void FAIR02地域偏好不发建议() {
    BiasSignal s =
        new BiasSignal(
            "S-2",
            BiasSignal.OwnershipType.PRIVATE,
            BiasSignal.RegionTier.EAST,
            BiasSignal.ScaleTier.MEDIUM,
            "本地优先",
            0.9);
    FairnessDecision d = service.evaluateFairness(s);
    assertThat(d.tier()).isEqualTo("MANUAL");
    assertThat(d.suggestionEmitted()).isFalse();
  }

  private static final class StubActor implements GovernanceActorPort {
    private String id;

    StubActor(String id) {
      this.id = id;
    }

    @Override
    public String currentActorId() {
      return id;
    }
  }
}
