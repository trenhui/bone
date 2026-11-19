package com.bone.procurement.governance.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 能力聚合状态机 + fail-closed + 审计留痕单测。 */
class AiCapabilityTest {

  private static final OwnerRef OWNER = OwnerRef.of("u-1", "张三");

  @Test
  void 登记缺省UNKNOWN不放行推理() {
    AiCapability c = AiCapability.register(new CapabilityCode("G15"), "公平治理", OWNER);
    assertThat(c.state()).isEqualTo(CapabilityState.UNKNOWN);
    assertThat(c.allowsInference()).isFalse();
    assertThat(c.trace()).hasSize(1);
    assertThat(c.trace().get(0).eventType()).isEqualTo("REGISTERED");
  }

  @Test
  void 启用后才放行推理() {
    AiCapability c = AiCapability.register(new CapabilityCode("M2"), "合同审查", OWNER);
    c.enable("admin-1", "评审通过");
    assertThat(c.state()).isEqualTo(CapabilityState.AVAILABLE);
    assertThat(c.allowsInference()).isTrue();
  }

  @Test
  void 一键停用与降级后不再放行() {
    AiCapability c = AiCapability.register(new CapabilityCode("M4"), "辅助评标", OWNER);
    c.enable("admin-1", "评审通过");
    c.disable("admin-1", "演练停机");
    assertThat(c.allowsInference()).isFalse();
    assertThat(c.state()).isEqualTo(CapabilityState.DISABLED);

    c.enable("admin-1", "恢复");
    c.degrade("admin-1", "漂移告警");
    assertThat(c.allowsInference()).isFalse();
    assertThat(c.state()).isEqualTo(CapabilityState.DEGRADED);
  }

  @Test
  void 不可判定态不得直接启用() {
    AiCapability c = AiCapability.register(new CapabilityCode("G16"), "自治车道", OWNER);
    c.markUndetermined("admin-1", "法务结论待决");
    assertThat(c.state()).isEqualTo(CapabilityState.UNDETERMINED);
    assertThatThrownBy(() -> c.enable("admin-1", "绕过"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("不可判定态");
    assertThat(c.allowsInference()).isFalse();
  }

  @Test
  void 降级仅允许从可用态触发() {
    AiCapability c = AiCapability.register(new CapabilityCode("M2"), "合同审查", OWNER);
    assertThatThrownBy(() -> c.degrade("admin-1", "非法降级"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("只有可用态");
  }

  @Test
  void 治理操作缺操作人一律拒绝() {
    AiCapability c = AiCapability.register(new CapabilityCode("M2"), "合同审查", OWNER);
    assertThatThrownBy(() -> c.disable("", "匿名停机"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("操作人");
    assertThatThrownBy(() -> c.enable(null, "自报")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 每次状态变更都留不可变追溯() {
    AiCapability c = AiCapability.register(new CapabilityCode("M2"), "合同审查", OWNER);
    c.enable("admin-1", "启用理由");
    c.disable("admin-2", "停用理由");
    assertThat(c.trace())
        .extracting(GovernanceTraceEntry::eventType)
        .containsExactly("REGISTERED", "ENABLED", "DISABLED");
    assertThat(c.trace().get(2).detail()).contains("admin-2").contains("停用理由");
  }

  @Test
  void 责任人变更留痕() {
    AiCapability c = AiCapability.register(new CapabilityCode("M2"), "合同审查", OWNER);
    c.reassignOwner(OwnerRef.of("u-2", "李四"), "admin-1", "岗位调整");
    assertThat(c.owner().displayName()).isEqualTo("李四");
    assertThat(c.trace()).extracting(GovernanceTraceEntry::eventType).contains("OWNER_REASSIGNED");
  }

  @Test
  void 空能力码被值对象拒绝() {
    assertThatThrownBy(() -> new CapabilityCode("   "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 空责任人被拒绝() {
    assertThatThrownBy(() -> OwnerRef.of("", "无名")).isInstanceOf(IllegalArgumentException.class);
  }
}
