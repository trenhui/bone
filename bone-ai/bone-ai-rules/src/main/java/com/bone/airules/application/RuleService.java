package com.bone.airules.application;

import com.bone.airules.domain.gateway.RuleEngine;
import com.bone.airules.domain.model.RuleAsset;
import com.bone.airules.domain.model.RuleAssetVersion;
import com.bone.airules.domain.model.RuleEvalResult;
import com.bone.airules.domain.model.RuleStatus;
import com.bone.airules.domain.repository.RuleAssetRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 规则治理应用服务：资产 CRUD + 版本发布 + 灰度 + 命中标记 + 评估入口。
 *
 * <p>「规则命中不调 LLM」由网关强制（RULE_HIT_BLOCK），本服务只负责产出短路信号； 规则文本保持声明式（比较 / 阈值 / 字符串匹配），复杂逻辑留产品 domain。
 */
public class RuleService {

  private static final Logger log = LoggerFactory.getLogger(RuleService.class);

  private final RuleAssetRepository repository;
  private final RuleEngine ruleEngine;

  public RuleService(RuleAssetRepository repository, RuleEngine ruleEngine) {
    this.repository = repository;
    this.ruleEngine = ruleEngine;
  }

  /** 创建规则草稿（code 租户内唯一）。 */
  public RuleAsset create(String tenantId, String code, String name, String description) {
    if (repository.findByCode(tenantId, code).isPresent()) {
      throw new IllegalArgumentException("rule code already exists: " + code);
    }
    RuleAsset rule = RuleAsset.create(tenantId, code, name, description);
    repository.save(rule);
    log.info("Rule created: code={}, tenant={}", code, tenantId);
    return rule;
  }

  /** 首版发布（草稿 → ACTIVE v1）。表达式发布前预检编译。 */
  public RuleAssetVersion publishFirst(
      String ruleId, String expression, boolean hitBlock, int grayPercent, String operator) {
    RuleAsset rule = require(ruleId);
    precheck(expression);
    RuleAssetVersion v = rule.publishFirst(expression, hitBlock, grayPercent, operator);
    repository.save(rule);
    log.info(
        "Rule v1 published: id={}, code={}, hitBlock={}, gray={}",
        ruleId,
        rule.getCode(),
        hitBlock,
        grayPercent);
    return v;
  }

  /** 发布新版本（版本 +1，历史留链；规则级兼容性检查由回归门禁在发布流水线强制）。 */
  public RuleAssetVersion publishNext(
      String ruleId, String expression, boolean hitBlock, int grayPercent, String operator) {
    RuleAsset rule = require(ruleId);
    precheck(expression);
    RuleAssetVersion v = rule.publishNext(expression, hitBlock, grayPercent, operator);
    repository.save(rule);
    log.info("Rule v published: id={}, code={}", v.getVersionNo(), ruleId, rule.getCode());
    return v;
  }

  /** 调整灰度比例（0-100；0=全量不灰度）。 */
  public void setGrayPercent(String ruleId, int grayPercent) {
    RuleAsset rule = require(ruleId);
    rule.changeGrayPercent(grayPercent);
    repository.save(rule);
  }

  /** 命中标记开关：RULE_HIT_BLOCK——命中时网关短路不调 LLM。 */
  public void setHitBlock(String ruleId, boolean hitBlock) {
    RuleAsset rule = require(ruleId);
    rule.toggleHitBlock(hitBlock);
    repository.save(rule);
  }

  /** 停用规则（版本链保留供回放）。 */
  public void deprecate(String ruleId) {
    RuleAsset rule = require(ruleId);
    rule.deprecate();
    repository.save(rule);
  }

  /**
   * 单规则评估：状态 / 灰度校验 → QLExpress 执行 → 组装短路信号。
   *
   * @param context 求值上下文；约定键 {@code traceId} 参与灰度分桶（同一请求判定稳定）
   */
  public RuleEvalResult evaluate(String ruleId, Map<String, Object> context, boolean trace) {
    RuleAsset rule = require(ruleId);
    return evaluateRule(rule, context, trace);
  }

  /** 批量评估租户内全部 ACTIVE 规则（编排规则节点调用；任一命中即短路信号可聚合）。 */
  public List<RuleEvalResult> evaluateAll(
      String tenantId, Map<String, Object> context, boolean trace) {
    List<RuleEvalResult> results = new ArrayList<>();
    for (RuleAsset rule : repository.findActive(tenantId)) {
      results.add(evaluateRule(rule, context, trace));
    }
    return results;
  }

  private RuleEvalResult evaluateRule(RuleAsset rule, Map<String, Object> context, boolean trace) {
    if (rule.getStatus() != RuleStatus.ACTIVE) {
      return RuleEvalResult.miss(rule.getCode(), rule.getCurrentVersionNo(), "NOT_ACTIVE", 0);
    }
    Object traceId = context == null ? null : context.get("traceId");
    if (!rule.inGrayBucket(traceId == null ? null : traceId.toString())) {
      return RuleEvalResult.miss(rule.getCode(), rule.getCurrentVersionNo(), "GRAY_OUT", 0);
    }
    RuleEngine.RuleEvaluation ev = ruleEngine.evaluate(rule.getExpression(), context, trace);
    if (ev.errorMessage() != null) {
      return RuleEvalResult.miss(
          rule.getCode(),
          rule.getCurrentVersionNo(),
          "EVAL_ERROR: " + ev.errorMessage(),
          ev.elapsedMs());
    }
    return ev.hit()
        ? RuleEvalResult.hit(
            rule.getCode(),
            rule.getCurrentVersionNo(),
            ev.tracePath(),
            ev.elapsedMs(),
            rule.isHitBlock())
        : RuleEvalResult.miss(
            rule.getCode(), rule.getCurrentVersionNo(), ev.tracePath(), ev.elapsedMs());
  }

  /** 表达式预检：发布前编译一次，语法错误立即拒绝（防坏规则进版本链）。 */
  private void precheck(String expression) {
    if (expression == null || expression.isBlank()) {
      throw new IllegalArgumentException("expression must not be blank");
    }
    RuleEngine.RuleEvaluation ev = ruleEngine.evaluate(expression, Map.of(), false);
    if (ev.errorMessage() != null) {
      throw new IllegalArgumentException("expression compile failed: " + ev.errorMessage());
    }
  }

  private RuleAsset require(String ruleId) {
    return Optional.ofNullable(repository.findById(ruleId))
        .orElseThrow(() -> new IllegalArgumentException("rule not found: " + ruleId));
  }
}
