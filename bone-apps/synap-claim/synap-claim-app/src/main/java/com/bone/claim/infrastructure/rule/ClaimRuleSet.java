package com.bone.claim.infrastructure.rule;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 规则资产（配置面与代码分离的最小承载）。
 *
 * <p>规则表达式、结论码与优先级从 {@code claim-ruleset.json} 读取，版本随命中明细落 {@code
 * t_claim_rule_hit.rule_version}，保证回放能回到当时那套规则。调整规则不再需要改 Java 与发版： 通过 {@code
 * bone.claim.ruleset.location} 指向外部文件即可；DB 热更 / 灰度 / 回归门禁仍属 bone-ai C2 RuleAsset 的 Phase B
 * 范围，本类不冒充已具备这些能力。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaimRuleSet(String version, List<RuleSpec> rules) {

  /** 单条规则；{@code risk} 决定命中是否计入反欺诈风险（大额复核档不计入）。 */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record RuleSpec(
      String code,
      String category,
      Integer priority,
      String expression,
      String conclusion,
      boolean risk) {}
}
