package com.bone.claim.application.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.ScriptConditionPort;
import com.bone.claim.application.port.out.ScriptConditionPort.ConditionVerdict;
import com.bone.claim.application.port.out.ScriptConditionPort.RejectReason;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 流程层：正常流转 + 四类失败关闭，并钉住「纯编排、零副作用」。 */
class FlowFireServiceTest {

  private static final String EVENT = "EVIDENCE_LOADED";
  private static final Map<String, Object> FACTS = Map.of("claimAmount", 12000);

  private static Stage stage() {
    return new Stage(
        "RuleCheck", new Trigger(EVENT, new Condition("claimAmount >= 10000")), "AiAssess");
  }

  @Test
  @DisplayName("条件成立推进到下一阶段")
  void advancesOnTrustedPass() {
    ScriptConditionPort port = mock(ScriptConditionPort.class);
    when(port.evaluate("claimAmount >= 10000", FACTS)).thenReturn(ConditionVerdict.pass(true));

    FlowOutcome outcome = new FlowFireService(port).fire(stage(), EVENT, FACTS);

    assertThat(outcome.advanced()).isTrue();
    assertThat(outcome.stage()).isEqualTo("RuleCheck");
    assertThat(outcome.nextStage()).isEqualTo("AiAssess");
    assertThat(outcome.reasonCode()).isNull();
  }

  @Test
  @DisplayName("条件不成立停止推进（可信失败，不是异常）")
  void haltsOnTrustedFalse() {
    ScriptConditionPort port = mock(ScriptConditionPort.class);
    when(port.evaluate("claimAmount >= 10000", FACTS)).thenReturn(ConditionVerdict.pass(false));

    FlowOutcome outcome = new FlowFireService(port).fire(stage(), EVENT, FACTS);

    assertThat(outcome.advanced()).isFalse();
    assertThat(outcome.reasonCode()).isEqualTo("CONDITION_FALSE");
  }

  @Test
  @DisplayName("越界脚本被拒时 fail-closed 且不带出下一阶段")
  void haltsOnRejectedVerdict() {
    ScriptConditionPort port = mock(ScriptConditionPort.class);
    when(port.evaluate("claimAmount >= 10000", FACTS))
        .thenReturn(ConditionVerdict.reject(RejectReason.FORBIDDEN_IDENTIFIER, "File"));

    FlowOutcome outcome = new FlowFireService(port).fire(stage(), EVENT, FACTS);

    assertThat(outcome.advanced()).isFalse();
    assertThat(outcome.nextStage()).isNull();
    assertThat(outcome.reasonCode()).isEqualTo("FORBIDDEN_IDENTIFIER");
  }

  @Test
  @DisplayName("求值端口抛异常时 fail-closed")
  void haltsWhenPortThrows() {
    ScriptConditionPort port = mock(ScriptConditionPort.class);
    when(port.evaluate("claimAmount >= 10000", FACTS)).thenThrow(new IllegalStateException("boom"));

    FlowOutcome outcome = new FlowFireService(port).fire(stage(), EVENT, FACTS);

    assertThat(outcome.advanced()).isFalse();
    assertThat(outcome.reasonCode()).isEqualTo("ENGINE_UNAVAILABLE");
  }

  @Test
  @DisplayName("沙箱不可用时 fail-closed，不在宿主环境直接执行脚本")
  void haltsWhenSandboxUnavailable() {
    FlowOutcome outcome = new FlowFireService(null).fire(stage(), EVENT, FACTS);

    assertThat(outcome.advanced()).isFalse();
    assertThat(outcome.reasonCode()).isEqualTo("ENGINE_UNAVAILABLE");
  }

  @Test
  @DisplayName("事件不匹配不触发求值")
  void haltsOnTriggerMismatch() {
    ScriptConditionPort port = mock(ScriptConditionPort.class);

    FlowOutcome outcome = new FlowFireService(port).fire(stage(), "OTHER_EVENT", FACTS);

    assertThat(outcome.advanced()).isFalse();
    assertThat(outcome.reasonCode()).isEqualTo("TRIGGER_NOT_MATCHED");
  }

  @Test
  @DisplayName("流程层不持有仓储或事件发布器，失败关闭时零业务状态写入")
  void hasNoStateWritingDependency() {
    Field[] instanceFields =
        Arrays.stream(FlowFireService.class.getDeclaredFields())
            .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
            .toArray(Field[]::new);

    assertThat(instanceFields).hasSize(1);
    assertThat(instanceFields[0].getType()).isEqualTo(ScriptConditionPort.class);
    assertThat(Arrays.stream(instanceFields).map(f -> f.getType().getName()))
        .noneMatch(name -> name.contains("Repository") || name.contains("EventPublisher"));
  }
}
