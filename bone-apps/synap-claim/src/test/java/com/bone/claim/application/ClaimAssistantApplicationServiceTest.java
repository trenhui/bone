package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.claim.application.ClaimAssistantApplicationService.AssistantReply;
import com.bone.claim.application.ClaimAssistantApplicationService.ToolDescriptor;
import com.bone.claim.application.ClaimAssistantApplicationService.ToolExecutionResult;
import com.bone.claim.application.port.out.AiGatewayPort;
import com.bone.claim.application.port.out.AiGatewayPort.AssistantQuery;
import com.bone.claim.application.port.out.AiGatewayPort.Completion;
import com.bone.claim.application.port.out.PolicyQueryPort;
import com.bone.claim.application.port.out.PolicyQueryPort.PolicySnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 智能理赔助手（WS-B P1）行为契约：<b>受控工具面必须是确定性的、如实降级的</b>。
 *
 * <p>守三件事：
 *
 * <ol>
 *   <li>工具清单固定且声明入参——不得静默增减，否则前端按旧清单调用会拿到未知工具异常；
 *   <li>工具执行<b>不依赖 LLM</b>：命中/未命中/缺参三种路径都返回可控结果，未命中绝不编造保单；
 *   <li>NL 入口在网关未就绪时<b>如实说未就绪</b>，绝不用空文本假装已回答（与 assess 的 fail-closed 同源）。
 * </ol>
 */
class ClaimAssistantApplicationServiceTest {

  private final AiGatewayPort gateway = mock(AiGatewayPort.class);
  private final PolicyQueryPort policyQueryPort = mock(PolicyQueryPort.class);
  private final ClaimAssistantApplicationService service =
      new ClaimAssistantApplicationService(gateway, policyQueryPort);

  private static PolicySnapshot snapshot(String policyNo) {
    return new PolicySnapshot(
        policyNo,
        "INS-" + policyNo,
        1L,
        "v1",
        List.of("hash-1", "hash-2"),
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-12-31T23:59:59Z"));
  }

  @Test
  @DisplayName("工具面固定为 2 个确定性工具，均可用且声明入参（Required 位正确）")
  void listsTwoDeterministicTools() {
    List<ToolDescriptor> tools = service.listTools();

    assertThat(tools)
        .extracting(ToolDescriptor::id)
        .containsExactly("policy-lookup", "policy-lookup-by-insurer");
    assertThat(tools).allMatch(ToolDescriptor::available);
    assertThat(tools.get(0).params())
        .extracting(ToolDescriptor.ToolParam::name)
        .containsExactly("policyNo", "planId");
    assertThat(tools.get(0).params().get(0).required()).isTrue();
    assertThat(tools.get(0).params().get(1).required()).isFalse();
  }

  @Test
  @DisplayName("policy-lookup 命中：success=true，正文带保单号与承保人数")
  void policyLookupHit() {
    when(policyQueryPort.findActivePolicy("P1", null)).thenReturn(Optional.of(snapshot("P1")));

    ToolExecutionResult result = service.runTool("policy-lookup", Map.of("policyNo", "P1"));

    assertThat(result.success()).isTrue();
    assertThat(result.text()).contains("P1").contains("承保被保人 2 人");
  }

  @Test
  @DisplayName("policy-lookup 未命中：success=false 且如实说明，不编造保单")
  void policyLookupMiss() {
    when(policyQueryPort.findActivePolicy("P1", null)).thenReturn(Optional.empty());

    ToolExecutionResult result = service.runTool("policy-lookup", Map.of("policyNo", "P1"));

    assertThat(result.success()).isFalse();
    assertThat(result.honestNote()).contains("未查到");
  }

  @Test
  @DisplayName("缺必填参数：success=false 且点名缺哪个参数，不打到 port")
  void policyLookupMissingParam() {
    ToolExecutionResult result = service.runTool("policy-lookup", Map.of());

    assertThat(result.success()).isFalse();
    assertThat(result.honestNote()).contains("policyNo");
  }

  @Test
  @DisplayName("按保司号多条命中：success=true 但给出「须按被保人收敛」的诚实说明，禁止静默取首条")
  void insurerLookupMultiHit() {
    when(policyQueryPort.findActivePoliciesByInsurerNo("INS1", null))
        .thenReturn(List.of(snapshot("P1"), snapshot("P2")));

    ToolExecutionResult result =
        service.runTool("policy-lookup-by-insurer", Map.of("insurerPolicyNo", "INS1"));

    assertThat(result.success()).isTrue();
    assertThat(result.text()).contains("命中 2 张");
    assertThat(result.honestNote()).contains("收敛");
  }

  @Test
  @DisplayName("未知工具：抛 IllegalArgumentException，不静默返回空结果")
  void unknownToolThrows() {
    assertThatThrownBy(() -> service.runTool("nope", Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("网关未就绪：aiAvailable=false 且如实说明「未就绪」，不假装已回答")
  void assistUnavailableIsHonest() {
    when(gateway.answer(any(AssistantQuery.class))).thenReturn(Completion.unavailable());

    AssistantReply reply = service.assist("这张保单还能报案吗？", null);

    assertThat(reply.aiAvailable()).isFalse();
    assertThat(reply.honestNote()).contains("未就绪");
  }

  @Test
  @DisplayName("空问题：抛 IllegalArgumentException，不打到网关")
  void blankQuestionRejected() {
    assertThatThrownBy(() -> service.assist("   ", null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
