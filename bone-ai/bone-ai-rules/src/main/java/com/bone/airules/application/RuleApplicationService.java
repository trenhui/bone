package com.bone.airules.application;

import com.bone.airules.common.AiRulesErrorCodes;
import com.bone.airules.domain.gateway.RuleEngine;
import com.bone.airules.domain.model.RegressionCase;
import com.bone.airules.domain.model.RuleAsset;
import com.bone.airules.domain.model.RuleAssetVersion;
import com.bone.airules.domain.model.RuleEvalResult;
import com.bone.airules.domain.model.RuleStatus;
import com.bone.airules.domain.repository.RuleAssetRepository;
import com.bone.airules.domain.repository.RuleAssetVersionRepository;
import com.bone.airules.domain.service.RuleRegressionGate;
import com.bone.core.exception.BizException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 规则治理应用服务：资产 CRUD + 版本发布（落库）+ 灰度 + 命中标记 + 回滚 + 评估入口。
 *
 * <p>「规则命中不调 LLM」由网关强制（RULE_HIT_BLOCK），本服务只负责产出短路信号； 规则文本保持声明式（比较 / 阈值 / 字符串匹配），复杂逻辑留产品 domain。
 *
 * <p><b>版本链落库（2026-10-17 复核修复）</b>：每次发布除更新 {@code rule_asset} 当前生效列外，必落一行 {@code
 * rule_asset_version} ——「当时生效的是哪条表达式、谁发布的」是合规回放硬需求，只在进程内留链会在重启后丢失。回滚 = 以历史表达式发布新版本（不历史篡改）， 旧当前版打
 * {@code ROLLED_BACK}。
 *
 * <p>异常一律携带 {@link AiRulesErrorCodes} 稳定码（check-error-code-landing 门禁：application 层禁裸异常）。
 */
public class RuleApplicationService {

  private static final Logger log = LoggerFactory.getLogger(RuleApplicationService.class);

  private final RuleAssetRepository repository;
  private final RuleAssetVersionRepository versionRepository;
  private final RuleEngine ruleEngine;

  public RuleApplicationService(
      RuleAssetRepository repository,
      RuleAssetVersionRepository versionRepository,
      RuleEngine ruleEngine) {
    this.repository = repository;
    this.versionRepository = versionRepository;
    this.ruleEngine = ruleEngine;
  }

  /** 创建规则草稿（code 租户内唯一）。 */
  public RuleAsset create(String tenantId, String code, String name, String description) {
    if (repository.findByCode(tenantId, code).isPresent()) {
      throw new BizException(
          409, "rule code already exists: " + code, AiRulesErrorCodes.CODE_DUPLICATED);
    }
    RuleAsset rule = RuleAsset.create(tenantId, code, name, description);
    repository.save(rule);
    log.info("Rule created: code={}, tenant={}", code, tenantId);
    return rule;
  }

  /** 首版发布（草稿 → ACTIVE v1）。表达式发布前预检编译；无回归样本时仅编译校验。 */
  public RuleAssetVersion publishFirst(
      String ruleId, String expression, boolean hitBlock, int grayPercent, String operator) {
    return publishFirst(ruleId, expression, hitBlock, grayPercent, operator, List.of());
  }

  /** 首版发布，带回归样本集：正/负例任一不符即拒绝发布（业界规则平台发布即回归，总案 §4.3）。 */
  public RuleAssetVersion publishFirst(
      String ruleId,
      String expression,
      boolean hitBlock,
      int grayPercent,
      String operator,
      List<RegressionCase> regressionCases) {
    RuleAsset rule = require(ruleId);
    precheck(expression);
    assertRegression(expression, regressionCases);
    RuleAssetVersion v = rule.publishFirst(expression, hitBlock, grayPercent, operator);
    repository.save(rule);
    persistVersion(v);
    log.info(
        "Rule v1 published: id={}, code={}, hitBlock={}, gray={}",
        ruleId,
        rule.getCode(),
        hitBlock,
        grayPercent);
    return v;
  }

  /** 发布新版本（版本 +1，历史留链；无回归样本时仅编译校验）。 */
  public RuleAssetVersion publishNext(
      String ruleId, String expression, boolean hitBlock, int grayPercent, String operator) {
    return publishNext(ruleId, expression, hitBlock, grayPercent, operator, List.of());
  }

  /** 发布新版本，带回归样本集：正/负例任一不符即拒绝（规则级兼容性检查在发布流水线强制）。 */
  public RuleAssetVersion publishNext(
      String ruleId,
      String expression,
      boolean hitBlock,
      int grayPercent,
      String operator,
      List<RegressionCase> regressionCases) {
    RuleAsset rule = require(ruleId);
    precheck(expression);
    assertRegression(expression, regressionCases);
    RuleAssetVersion v = rule.publishNext(expression, hitBlock, grayPercent, operator);
    repository.save(rule);
    persistVersion(v);
    log.info("Rule v published: id={}, code={}", v.getVersionNo(), ruleId, rule.getCode());
    return v;
  }

  /**
   * 回滚到指定版本：以历史表达式 / 灰度 / 命中标记发布<b>新版本</b>（版本号 +1，不历史篡改），旧当前版打 ROLLED_BACK。
   *
   * <p>为何不「把旧版改回 ACTIVE」：版本链不可变是回放可信度的前提——历史行必须保持发布时的原样。
   */
  public RuleAssetVersion rollback(String ruleId, int targetVersionNo, String operator) {
    RuleAsset rule = require(ruleId);
    RuleAssetVersion target =
        versionRepository
            .findByRuleIdAndVersionNo(ruleId, targetVersionNo)
            .orElseThrow(
                () ->
                    new BizException(
                        404,
                        "rule version not found: " + ruleId + "@v" + targetVersionNo,
                        AiRulesErrorCodes.VERSION_NOT_FOUND));
    if (target.getVersionNo() == rule.getCurrentVersionNo()) {
      throw new BizException(
          409,
          "target version is already current: v" + targetVersionNo,
          AiRulesErrorCodes.ALREADY_CURRENT_VERSION);
    }
    // 旧当前版标记 ROLLED_BACK（历史留链、可回放）
    versionRepository
        .findByRuleIdAndVersionNo(ruleId, rule.getCurrentVersionNo())
        .ifPresent(
            current -> {
              current.markRolledBack();
              versionRepository.update(current);
            });
    RuleAssetVersion v =
        rule.publishNext(
            target.getExpression(), target.isHitBlock(), target.getGrayPercent(), operator);
    repository.save(rule);
    persistVersion(v);
    log.info(
        "Rule rolled back: id={}, to=v{}, newVersion=v{}",
        ruleId,
        targetVersionNo,
        v.getVersionNo());
    return v;
  }

  /** 版本链查询（版本号降序——回放 / diff 的管理面读）。 */
  public List<RuleAssetVersion> history(String ruleId) {
    require(ruleId);
    return versionRepository.findByRuleId(ruleId);
  }

  /** 回归门禁：正/负例任一不符即拒绝发布，失败明细逐条回显标签。 */
  private void assertRegression(String expression, List<RegressionCase> cases) {
    if (cases == null || cases.isEmpty()) {
      return;
    }
    RuleRegressionGate.RegressionResult result =
        new RuleRegressionGate(ruleEngine).evaluate(expression, cases);
    if (!result.passed()) {
      List<String> failed =
          result.cases().stream()
              .filter(c -> !c.passed())
              .map(c -> c.label() + "(期望 " + c.expectHit() + ", 实际 " + c.actualHit() + ")")
              .toList();
      throw new BizException(
          422,
          "rule regression failed: " + String.join("; ", failed),
          AiRulesErrorCodes.REGRESSION_FAILED);
    }
  }

  /** 调整灰度比例（0-100；0=全量不灰度）。 */
  public void setGrayPercent(String ruleId, int grayPercent) {
    RuleAsset rule = require(ruleId);
    try {
      rule.changeGrayPercent(grayPercent);
    } catch (IllegalArgumentException e) {
      throw new BizException(422, e.getMessage(), AiRulesErrorCodes.GRAY_OUT_OF_RANGE);
    }
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
      throw new BizException(
          422, "expression must not be blank", AiRulesErrorCodes.EXPRESSION_INVALID);
    }
    RuleEngine.RuleEvaluation ev = ruleEngine.evaluate(expression, Map.of(), false);
    if (ev.errorMessage() != null) {
      throw new BizException(
          422,
          "expression compile failed: " + ev.errorMessage(),
          AiRulesErrorCodes.EXPRESSION_INVALID);
    }
  }

  /** 版本快照落库（与 rule_asset 当前生效列同步——前者是历史真相，后者是执行入口）。 */
  private void persistVersion(RuleAssetVersion version) {
    versionRepository.save(version);
  }

  private RuleAsset require(String ruleId) {
    return Optional.ofNullable(repository.findById(ruleId))
        .orElseThrow(
            () -> new BizException(404, "rule not found: " + ruleId, AiRulesErrorCodes.NOT_FOUND));
  }
}
