package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.adapter.web.controller.AiCapabilityReadinessController;
import com.bone.claim.adapter.web.controller.AiCapabilityReadinessController.GuardCheckResp;
import com.bone.claim.application.port.out.AiAutoApproveGatePort;
import com.bone.claim.application.port.out.AiAutoApproveGatePort.Verdict;
import com.bone.claim.application.port.out.GraphFraudPort;
import com.bone.claim.application.port.out.KnowledgeRetrievalPort;
import com.bone.claim.application.port.out.RuleEnginePort;
import com.bone.claim.application.port.out.VisualForensicsPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.core.exception.BizException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * P2-2 判别力测试：AI 守门从"展示"变成"拦截"。
 *
 * <p>变异验证：删掉 {@code applyAutoDecision} 的前置 guard 判定 ⇒ 首条用例红（没有业务码异常，静默放行）； 把 guard-check 的
 * wouldBlock 恒 false ⇒ 第三条用例红。
 */
class AiAutoApproveGateTest {

  private static final long CLAIM = 6001L;

  @Test
  @DisplayName("blocker 非空 → 自动通过抛 SC_CLAIM_AI_GATE_BLOCKED，绝不结案建单")
  void blockerFailsClosedBeforeAnyMoneyAction() {
    AutoApprovalApplicationService service = serviceWithGate(mockGate());
    AiAutoApproveGatePort gate =
        (AiAutoApproveGatePort) ReflectionTestUtils.getField(service, "aiAutoApproveGate");
    ClaimCaseApplicationService claims =
        (ClaimCaseApplicationService)
            ReflectionTestUtils.getField(service, "claimCaseApplicationService");
    SettlementApplicationService settlements =
        (SettlementApplicationService)
            ReflectionTestUtils.getField(service, "settlementApplicationService");

    BizException ex =
        catchThrowableOfType(() -> service.applyAutoDecision(CLAIM), BizException.class);

    assertThat(ex).isNotNull();
    assertThat(ex.getErrorCode()).isEqualTo(ClaimErrorCodes.SC_CLAIM_AI_GATE_BLOCKED);
    assertThat(ex.getMessage()).contains("AUTO_APPROVE_TOGGLE_OFF");
    verify(gate).guard(CLAIM);
    verify(claims, never()).autoApprove(anyLong(), anyString());
    verify(settlements, never())
        .create(anyLong(), anyInt(), org.mockito.ArgumentMatchers.any(), anyString());
  }

  @Test
  @DisplayName("readiness 默认守门实现：发布开关关闭 → wouldBlock=true 且带受控 blocker")
  void readinessDefaultGuardBlocksWhenToggleOff() {
    AutoApprovalApplicationService auto = mock(AutoApprovalApplicationService.class);
    when(auto.isAutoApproveEnabled()).thenReturn(false);
    AiCapabilityReadinessService readiness =
        new AiCapabilityReadinessService(
            mock(RuleEnginePort.class),
            mock(VisualForensicsPort.class),
            mock(GraphFraudPort.class),
            mock(KnowledgeRetrievalPort.class),
            mock(com.bone.claim.application.port.out.AiGatewayPort.class),
            auto);

    Verdict verdict = readiness.guard(CLAIM);

    assertThat(verdict.wouldBlock()).isTrue();
    assertThat(verdict.blockers()).contains("AUTO_APPROVE_TOGGLE_OFF");
    assertThat(verdict.scopeNote()).contains("不构成放款许可");
  }

  @Test
  @DisplayName("guard-check 是只读预演：wouldBlock=true 透出 blocker 与 scopeNote，且带只读权限点")
  void guardCheckPreflightReportsBlock() throws NoSuchMethodException {
    AiCapabilityReadinessService readiness = mock(AiCapabilityReadinessService.class);
    when(readiness.guard(CLAIM))
        .thenReturn(Verdict.of(List.of("C5_LLM:UNKNOWN:DECLARATION_MISSING"), "部署态清单，不构成放款许可"));

    GuardCheckResp body =
        new AiCapabilityReadinessController(readiness).guardCheck(CLAIM).getData();

    assertThat(body.wouldBlock()).isTrue();
    assertThat(body.blockers()).containsExactly("C5_LLM:UNKNOWN:DECLARATION_MISSING");
    assertThat(body.scopeNote()).contains("不构成放款许可");
    Method method = AiCapabilityReadinessController.class.getMethod("guardCheck", long.class);
    PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).isEqualTo("hasAuthority('claim:claims:read')");
  }

  private static AiAutoApproveGatePort mockGate() {
    AiAutoApproveGatePort gate = mock(AiAutoApproveGatePort.class);
    when(gate.guard(CLAIM))
        .thenReturn(Verdict.of(List.of("AUTO_APPROVE_TOGGLE_OFF"), "部署态清单，不构成放款许可"));
    return gate;
  }

  /** 只搭自动通过服务的最小基座：闸门在一切查询之前拦截，其余依赖不会被触达。 */
  private static AutoApprovalApplicationService serviceWithGate(AiAutoApproveGatePort gate) {
    AutoApprovalApplicationService service =
        new AutoApprovalApplicationService(
            mock(ClaimCaseApplicationService.class),
            mock(SettlementApplicationService.class),
            mock(IndemnityApplicationService.class),
            mock(DecisionExecutionApplicationService.class),
            mock(FeatureTenantToggleService.class),
            mock(AiRolloutApplicationService.class));
    ReflectionTestUtils.setField(service, "enabled", true);
    ReflectionTestUtils.setField(service, "maxAmount", new BigDecimal("1000"));
    ReflectionTestUtils.setField(service, "aiGatewayBaseUrl", "https://api.example.com/v1");
    ReflectionTestUtils.setField(service, "aiAutoApproveGate", gate);
    return service;
  }
}
