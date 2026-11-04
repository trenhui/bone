package com.bone.claim.infrastructure.rule;

import com.bone.airules.domain.gateway.RuleEngine;
import com.bone.airules.infrastructure.engine.QlExpressRuleEngine;
import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.RuleEnginePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 核责规则引擎适配器（G2-1）：复用 bone-ai C2 的 QLExpress4 沙箱求值，替换一期 {@code available=false} 桩。
 *
 * <p>与 {@link LocalRuleEnginePortAdapter} 互斥装配：默认走本实现（{@code bone.claim.rule-engine.mode=ql} 或缺省），设
 * {@code mode=stub} 可秒级回退到 fail-closed 桩。
 *
 * <p><b>安全契约不变</b>：本适配器只做「核责 + 反欺诈硬闸的规则这一路」，金额仍由领域聚合 {@code Adjustment.calculate} 确定性产出（F1：AI /
 * 规则都不碰钱）；{@link #calculatePayout} 仍只返回分诊估算额。 视觉 E1 / 图计算 E2 未接入前，{@code RuleCheckNodeHandler}
 * 的三路硬闸依然判不过——故接入本引擎 <b>不会</b>解锁自动放款，只让规则结论与命中留痕从「默认值」变成「真实事实」。
 *
 * <p>规则集为版本化常量（{@link #RULE_VERSION}），表达式与 Rulebook 的 R-FRAUD / R-EVID / R-AMOUNT 口径对齐；后续接
 * bone-ai-rules 的 RuleAsset 热更 / 灰度 / 回归门禁时，只需把 {@link #RULES} 换成 按租户加载的版本链，调用方零改动。
 */
@Component
@ConditionalOnProperty(
    name = "bone.claim.rule-engine.mode",
    havingValue = "ql",
    matchIfMissing = true)
public class QlExpressRuleEnginePortAdapter implements RuleEnginePort {

  /** 规则集版本：改动任一表达式 / 结论码必须同步 bump，否则回放无法回到当时那套规则。 */
  static final String RULE_VERSION = "claim-ruleset-v1";

  /** 规则路径置信度：确定性规则的语义是「已执行且结论唯一」，不是统计置信度；沿用一期基线值， 不引入虚假校准。真正需要数据驱动校准的是 AI 置信度（见 G4-1）。 */
  private static final BigDecimal RULE_CONFIDENCE = new BigDecimal("0.90");

  private static final List<RuleSpec> RULES =
      List.of(
          new RuleSpec(
              "R-FRAUD-06", "FRAUD", 10, "verifyPassed == false", "VERIFY_NOT_PASSED", true),
          new RuleSpec(
              "R-EVID-01",
              "EVIDENCE",
              20,
              "evidenceParsed == false",
              "EVIDENCE_PARSE_FAILED",
              true),
          new RuleSpec(
              "R-OCR-01", "EVIDENCE", 30, "minOcrConfidence < 0.7", "OCR_CONFIDENCE_LOW", true),
          new RuleSpec(
              "R-AMOUNT-01", "AMOUNT", 40, "claimAmount >= 10000", "AMOUNT_OVER_LIMIT", false));

  private final RuleEngine engine = new QlExpressRuleEngine();
  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public RuleCheckResult check(ClaimContextPort.ClaimContextView ctx) {
    Map<String, Object> facts = factsOf(ctx);
    String snapshot = snapshotOf(facts);
    List<RuleHit> hits = new ArrayList<>();
    boolean riskHit = false;
    for (RuleSpec rule : RULES) {
      RuleEngine.RuleEvaluation evaluation = engine.evaluate(rule.expression(), facts, false);
      // 求值异常按未命中处理由 QlExpressRuleEngine 负责；此处只采信 hit=true 的确定性结论
      if (evaluation.hit()) {
        hits.add(
            new RuleHit(
                rule.code(),
                RULE_VERSION,
                rule.category(),
                rule.priority(),
                snapshot,
                rule.conclusion()));
        riskHit |= rule.risk();
      }
    }
    // 硬闸：验真通过且未命中风险规则；大额复核档（R-AMOUNT-01）不算风险，避免与分诊 AMOUNT_OVER_LIMIT 重复归因
    boolean fraudGatePassed = ctx.evidenceAllVerifyPassed() && !riskHit;
    return new RuleCheckResult(
        true, false, riskHit, fraudGatePassed, RULE_CONFIDENCE, List.copyOf(hits));
  }

  @Override
  public BigDecimal calculatePayout(ClaimContextPort.ClaimContextView ctx) {
    // F1：分诊估算额（= 申请金额），仅供档位判断，绝不进入资金链路
    return ctx.claimAmount();
  }

  /** 只读上下文 → 规则求值变量；数值统一转 double，避免 BigDecimal 与 DSL 字面量比较的类型歧义。 */
  private static Map<String, Object> factsOf(ClaimContextPort.ClaimContextView ctx) {
    Map<String, Object> facts = new LinkedHashMap<>();
    facts.put("claimType", String.valueOf(ctx.claimType()));
    facts.put("claimAmount", ctx.claimAmount() == null ? 0.0 : ctx.claimAmount().doubleValue());
    facts.put("hasEvidence", ctx.hasEvidence());
    facts.put("evidenceParsed", ctx.evidenceAllParsed());
    // null = 未提供 OCR 置信度，按 DecisionTriage 口径不判低（不误报），而非当作 0
    facts.put(
        "minOcrConfidence",
        ctx.minOcrConfidence() == null ? 1.0 : ctx.minOcrConfidence().doubleValue());
    facts.put("verifyPassed", ctx.evidenceAllVerifyPassed());
    return facts;
  }

  private String snapshotOf(Map<String, Object> facts) {
    try {
      return mapper.writeValueAsString(facts);
    } catch (Exception e) {
      // 留痕快照失败不应中断决策主链路：返回空对象，审计可据此识别快照缺失
      return "{}";
    }
  }

  /** 版本化规则定义；{@code risk} 决定命中是否计入反欺诈风险（大额复核档不计入）。 */
  private record RuleSpec(
      String code,
      String category,
      Integer priority,
      String expression,
      String conclusion,
      boolean risk) {}
}
