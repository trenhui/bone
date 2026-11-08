package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.claim.application.AiRuntimeHealthApplicationService.AiRuntimeHealth;
import com.bone.claim.domain.model.decision.DecisionTraceNode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 幻觉计量的聚合口径：模型给出但材料里无出处的定位条数，必须能在报表上看见。
 *
 * <p><b>为什么这条计量不能省</b>：适配器会把不可核的事实/定位丢弃——丢弃保证展示面干净，但也把"模型在编"这件事
 * 一起藏起来了。若只丢不计数，「模型什么都没抽到」与「模型抽了一堆全不可核」在两份报表上完全一样， 而后者恰恰是提示词与材料格式错配的早期信号（实测第一版就出现了编造 {@code
 * claimContext} 定位）。
 *
 * <p><b>为什么只统计模型跑过的节点</b>：降级节点没有模型输出，把它恒为 0 的计量并进分母，会把编造率稀释成 一个看起来无害的数字。故计数只在 {@code
 * aiAvailable=true} 分支累加，历史缺字段另设一桶。
 */
class AiRuntimeHealthLocatorTest {

  private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-11-02T00:00:00Z");

  @Test
  @DisplayName("模型参与的节点按条累加编造数；降级节点不参与该计量")
  void onlyModelNodesContribute() {
    AiRuntimeHealth health =
        AiRuntimeHealthApplicationService.aggregate(
            List.of(
                node(
                    "{\"aiAvailable\":true,\"aiModel\":\"deepseek-flash\",\"aiUnverifiableLocators\":3}"),
                node(
                    "{\"aiAvailable\":true,\"aiModel\":\"deepseek-flash\",\"aiUnverifiableLocators\":0}"),
                // 降级：没有模型输出，不存在编造
                node("{\"aiAvailable\":false,\"aiUnavailableReason\":\"GATEWAY_ERROR\"}")),
            FROM,
            TO);

    assertThat(health.aiUnverifiableLocators()).isEqualTo(3);
    assertThat(health.aiLocatorUnattributed()).isZero();
  }

  @Test
  @DisplayName("零编造与「老数据没这个字段」必须分开：前者是健康，后者是缺证据")
  void missingKeyIsNotZero() {
    AiRuntimeHealth health =
        AiRuntimeHealthApplicationService.aggregate(
            List.of(
                node("{\"aiAvailable\":true,\"aiModel\":\"deepseek-flash\"}"),
                node(
                    "{\"aiAvailable\":true,\"aiModel\":\"local-stub\",\"aiUnverifiableLocators\":1}")),
            FROM,
            TO);

    // 老数据（没键）不计为 0 编造，单独成桶；桩节点也有模型输出，照常计入
    assertThat(health.aiLocatorUnattributed()).isEqualTo(1);
    assertThat(health.aiUnverifiableLocators()).isEqualTo(1);
  }

  @Test
  @DisplayName("非整数的字段值不能静默变 0：按缺失处理并计入 unattributed")
  void nonNumericValueIsTreatedAsMissing() {
    AiRuntimeHealth health =
        AiRuntimeHealthApplicationService.aggregate(
            List.of(
                node(
                    "{\"aiAvailable\":true,\"aiModel\":\"deepseek-flash\",\"aiUnverifiableLocators\":\"3\"}")),
            FROM,
            TO);

    assertThat(health.aiUnverifiableLocators()).isZero();
    assertThat(health.aiLocatorUnattributed()).isEqualTo(1);
  }

  private static DecisionTraceNode node(String output) {
    DecisionTraceNode node = mock(DecisionTraceNode.class);
    when(node.getOutput()).thenReturn(output);
    return node;
  }
}
