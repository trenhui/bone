package com.bone.claim.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.DecisionNodeHandler.DecisionNodeContext;
import com.bone.claim.application.port.out.DecisionNodeHandler.DecisionNodeOutcome;
import com.bone.claim.application.port.out.GraphFraudPort;
import com.bone.claim.application.port.out.RuleEnginePort;
import com.bone.claim.application.port.out.VisualForensicsPort;
import com.bone.claim.domain.model.decision.DecisionGraph;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 决策节点 {@code RuleCheck} 的降级契约测试。
 *
 * <p>锁定的不变量：
 *
 * <ol>
 *   <li><b>硬闸 fail-closed</b>：视觉 E1 / 图计算 E2 任一未真实执行（{@code available=false}）即判定闸不过， 「不可用」绝不等同于「无篡改
 *       / 未命中团伙」——一台没接线的引擎不能让链路自动放行；
 *   <li><b>规则路可用性必须可辨</b>：规则桩未执行任何规则，结论里 {@code ruleAvailable=false} 且 {@code
 *       ruleUnavailableReason=RULE_ENGINE_STUB}——否则 {@code ruleConflict=false} 会被误读为「已核责且无冲突」；
 *   <li><b>原因码可区分</b>：{@code fraudGateReason} 必须区分「能力未集成」与「真实命中风险 / 验真未过」，
 *       否则核赔员无法判断该案件是"没查"还是"查出了问题"。
 * </ol>
 *
 * <p><b>口径边界</b>：规则路可用性当前只做标注、不参与硬闸（ADR-002 一期豁免允许 C2 为桩）；把它并入硬闸会使 AUTO_APPROVE
 * 策略整体翻转为人工，属资金路径策略变更，须签核后另起改动。本类不锁定该策略，只锁定"信号可见"。
 */
class RuleCheckNodeHandlerTest {

  private static final long CLAIM = 1001L;

  // ===================== 夹具 =====================

  private static ClaimContextPort.ClaimContextView ctx(boolean verifyPassed) {
    return new ClaimContextPort.ClaimContextView(
        CLAIM,
        "OUTPATIENT",
        new BigDecimal("800.00"),
        true,
        true,
        new BigDecimal("0.95"),
        verifyPassed);
  }

  private static RuleCheckNodeHandler handler(
      RuleEnginePort.RuleCheckResult rule,
      VisualForensicsPort.ForensicsResult visual,
      GraphFraudPort.FraudResult graph,
      boolean verifyPassed) {
    ClaimContextPort contextPort = claimId -> ctx(verifyPassed);
    RuleEnginePort rulePort =
        new RuleEnginePort() {
          @Override
          public RuleCheckResult check(ClaimContextPort.ClaimContextView c) {
            return rule;
          }

          @Override
          public BigDecimal calculatePayout(ClaimContextPort.ClaimContextView c) {
            return c.claimAmount();
          }
        };
    VisualForensicsPort visualPort = c -> visual;
    GraphFraudPort graphPort = c -> graph;
    return new RuleCheckNodeHandler(contextPort, rulePort, visualPort, graphPort);
  }

  /** 规则桩：未真实执行（available=false），但仍给出默认「无冲突 / 无风险 / 0.90」。 */
  private static RuleEnginePort.RuleCheckResult stubRule(boolean verifyPassed) {
    return new RuleEnginePort.RuleCheckResult(
        false, false, false, verifyPassed, new BigDecimal("0.90"));
  }

  /** 真实执行且干净的规则结论。 */
  private static RuleEnginePort.RuleCheckResult cleanRule() {
    return new RuleEnginePort.RuleCheckResult(true, false, false, true, new BigDecimal("0.90"));
  }

  /** 真实执行但证据验真未过。 */
  private static RuleEnginePort.RuleCheckResult verifyFailedRule() {
    return new RuleEnginePort.RuleCheckResult(true, false, false, false, new BigDecimal("0.90"));
  }

  private static VisualForensicsPort.ForensicsResult visual(boolean available, boolean tampered) {
    return new VisualForensicsPort.ForensicsResult(tampered, BigDecimal.ZERO, available);
  }

  private static GraphFraudPort.FraudResult graph(boolean available, boolean hit) {
    return new GraphFraudPort.FraudResult(hit, hit ? "C-1" : null, available);
  }

  private static Map<String, Object> run(RuleCheckNodeHandler handler) {
    DecisionNodeOutcome outcome =
        handler.execute(new DecisionNodeContext(CLAIM, "trace-1", null, Map.of()));
    return outcome.output();
  }

  // ===================== 用例 =====================

  @Test
  @DisplayName("规则桩（未执行任何规则）→ 显式标注 ruleAvailable=false + 原因码，不得伪装成已核责")
  void ruleStubIsFlaggedUnavailable() {
    Map<String, Object> out =
        run(handler(stubRule(true), visual(true, false), graph(true, false), true));

    assertThat(out.get("ruleAvailable")).isEqualTo(false);
    assertThat(out.get("ruleUnavailableReason")).isEqualTo("RULE_ENGINE_STUB");
    // 桩给出的 riskHit=false 是默认值，仍不得据此宣称命中风险
    assertThat(out.get("riskHit")).isEqualTo(false);
  }

  @Test
  @DisplayName("三路真实执行且均干净 → 闸通过，原因=PASSED")
  void allThreeRoutesRunCleanPasses() {
    Map<String, Object> out =
        run(handler(cleanRule(), visual(true, false), graph(true, false), true));

    assertThat(out.get("ruleAvailable")).isEqualTo(true);
    assertThat(out.get("visualAvailable")).isEqualTo(true);
    assertThat(out.get("graphAvailable")).isEqualTo(true);
    assertThat(out.get("fraudGatePassed")).isEqualTo(true);
    assertThat(out.get("fraudGateReason")).isEqualTo("PASSED");
    assertThat(out.get("ruleUnavailableReason")).isEqualTo("");
  }

  @Test
  @DisplayName("三路可用但图计算命中风险 → riskHit=true 且闸不过，原因=RISK_HIT（区别于能力缺失）")
  void capabilityPresentButRiskHit() {
    Map<String, Object> out =
        run(handler(cleanRule(), visual(true, false), graph(true, true), true));

    assertThat(out.get("riskHit")).isEqualTo(true);
    assertThat(out.get("fraudGatePassed")).isEqualTo(false);
    assertThat(out.get("fraudGateReason")).isEqualTo("RISK_HIT");
  }

  @Test
  @DisplayName("三路可用但证据验真未过 → 闸不过，原因=VERIFY_NOT_PASSED")
  void capabilityPresentButVerifyFailed() {
    Map<String, Object> out =
        run(handler(verifyFailedRule(), visual(true, false), graph(true, false), false));

    assertThat(out.get("fraudGatePassed")).isEqualTo(false);
    assertThat(out.get("fraudGateReason")).isEqualTo("VERIFY_NOT_PASSED");
  }

  @Test
  @DisplayName("视觉 E1 未执行 → 闸不过，原因=能力缺失（不可用 ≠ 无篡改）")
  void visualMissingFailsClosed() {
    Map<String, Object> out =
        run(handler(cleanRule(), visual(false, false), graph(true, false), true));

    assertThat(out.get("visualAvailable")).isEqualTo(false);
    assertThat(out.get("fraudGatePassed")).isEqualTo(false);
    assertThat(out.get("fraudGateReason")).isEqualTo("ANTI_FRAUD_CAPABILITY_MISSING");
  }

  @Test
  @DisplayName("图计算 E2 未执行 → 闸不过（历史回归：E1/E2 未集成时一律转人工）")
  void graphMissingFailsClosed() {
    Map<String, Object> out =
        run(handler(cleanRule(), visual(true, false), graph(false, false), true));

    assertThat(out.get("graphAvailable")).isEqualTo(false);
    assertThat(out.get("fraudGatePassed")).isEqualTo(false);
    assertThat(out.get("fraudGateReason")).isEqualTo("ANTI_FRAUD_CAPABILITY_MISSING");
  }

  @Test
  @DisplayName("视觉未执行时其 tampered 不得被采信为风险（也不得被当作干净）")
  void unavailableVisualSignalIsNotTrusted() {
    Map<String, Object> out =
        run(handler(cleanRule(), visual(false, true), graph(true, false), true));

    assertThat(out.get("riskHit")).isEqualTo(false);
    assertThat(out.get("fraudGatePassed")).isEqualTo(false);
  }

  @Test
  @DisplayName("节点名与决策图节点一致（装配自证，防节点名漂移导致永不被调度）")
  void nodeNameMatchesGraph() {
    RuleCheckNodeHandler handler =
        handler(cleanRule(), visual(true, false), graph(true, false), true);
    assertThat(handler.nodeName()).isEqualTo(DecisionGraph.RULE_CHECK);
  }
}
