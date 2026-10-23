package com.bone.aiapp.web;

import com.bone.aiapp.config.AiTenantContext;
import com.bone.airules.application.RuleApplicationService;
import com.bone.airules.application.RuleQueryApplicationService;
import com.bone.airules.common.AiRulesErrorCodes;
import com.bone.airules.domain.model.RuleAsset;
import com.bone.airules.domain.model.RuleAssetVersion;
import com.bone.core.exception.BizException;
import com.bone.core.model.ApiResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 生产拓扑：规则资产 REST 管理面（详设附录 A「规则」落地；管理员 / 规范流程消费，权限复用 bone-iam）。
 *
 * <p>此前规则治理只有进程内服务，运营改规则要发版或连库——本控制器把治理动作暴露为 HTTP：创建 / 发布（首版与新版按历史自动分流）/
 * 回滚 / 灰度 / 命中标记 / 废弃 / 试算求值。<b>发布即回归</b>的强约束留在服务层（{@code RuleApplicationService} 预检编译 +
 * 正负例断言，不因 HTTP 化而放宽）；本面只做「把治理动作送到服务」，不复制任何规则语义。
 *
 * <p>租户一律从上下文取（详设 §3.1）；写操作的操作人（{@code operator}）由请求体显式给出——它是审计字段不是安全边界，
 * 与「租户禁止入参传」不矛盾：租户决定数据边界，operator 只记录谁按的按钮。
 */
@RestController
@RequestMapping("/api/ai/v1/rules")
public class RuleController {

  private final RuleApplicationService ruleService;
  private final RuleQueryApplicationService ruleQuery;
  private final AiTenantContext tenantContext;

  public RuleController(
      RuleApplicationService ruleService,
      RuleQueryApplicationService ruleQuery,
      AiTenantContext tenantContext) {
    this.ruleService = ruleService;
    this.ruleQuery = ruleQuery;
    this.tenantContext = tenantContext;
  }

  /** 生效规则列表（ACTIVE；管理面下拉 / 对账用）。 */
  @GetMapping
  public ApiResponse<List<RuleAsset>> activeRules() {
    return ApiResponse.success(ruleQuery.findActive(tenantContext.requireTenantId()));
  }

  /** 规则详情（含当前生效版本号与表达式）。 */
  @GetMapping("/{ruleId}")
  public ApiResponse<RuleAsset> byId(@PathVariable String ruleId) {
    Optional<RuleAsset> rule = ruleQuery.findById(ruleId);
    return ApiResponse.success(requireOwned(rule, ruleId));
  }

  /** 版本链（发布历史：谁在何时发布了什么，回滚选版本的依据）。 */
  @GetMapping("/{ruleId}/history")
  public ApiResponse<List<RuleAssetVersion>> history(@PathVariable String ruleId) {
    requireOwned(ruleQuery.findById(ruleId), ruleId); // 先验存在与租户，再给历史
    return ApiResponse.success(ruleService.history(ruleId));
  }

  /** 创建规则草稿（code 租户内唯一，重复即 409 由服务层断言）。 */
  @PostMapping
  public ApiResponse<RuleAsset> create(@RequestBody CreateRuleRequest request) {
    RuleAsset rule =
        ruleService.create(
            tenantContext.requireTenantId(), request.code(), request.name(), request.description());
    return ApiResponse.success(rule);
  }

  /**
   * 发布：无历史 ⇒ 首版（publishFirst）；有历史 ⇒ 新版（publishNext）。
   *
   * <p>分流逻辑放控制器而非让调用方记两个端点——「发一条规则」是运营心智，首版/新版是实现细节。 发布强约束（编译预检 /
   * 回归样本）在服务层，见 {@code RuleApplicationService}。
   */
  @PostMapping("/{ruleId}/publish")
  public ApiResponse<RuleAssetVersion> publish(
      @PathVariable String ruleId, @RequestBody PublishRuleRequest request) {
    requireOwned(ruleQuery.findById(ruleId), ruleId);
    RuleAssetVersion version =
        ruleService.history(ruleId).isEmpty()
            ? ruleService.publishFirst(
                ruleId, request.expression(), request.hitBlock(), request.grayPercent(), request.operator())
            : ruleService.publishNext(
                ruleId, request.expression(), request.hitBlock(), request.grayPercent(), request.operator());
    return ApiResponse.success(version);
  }

  /** 回滚到指定版本（以历史内容发新版本，版本链不可变；旧当前版打 ROLLED_BACK）。 */
  @PostMapping("/{ruleId}/rollback")
  public ApiResponse<RuleAssetVersion> rollback(
      @PathVariable String ruleId, @RequestBody RollbackRuleRequest request) {
    requireOwned(ruleQuery.findById(ruleId), ruleId);
    return ApiResponse.success(ruleService.rollback(ruleId, request.targetVersionNo(), request.operator()));
  }

  /** 调整灰度比例（0-100，越界由服务层断言 {@code AI_RULE_GRAY_OUT_OF_RANGE}）。 */
  @PostMapping("/{ruleId}/gray")
  public ApiResponse<Void> setGray(@PathVariable String ruleId, @RequestBody GrayRuleRequest request) {
    requireOwned(ruleQuery.findById(ruleId), ruleId);
    ruleService.setGrayPercent(ruleId, request.grayPercent());
    return ApiResponse.success();
  }

  /** 命中标记开关：{@code hitBlock=true} = 规则命中即网关短路不调 LLM（成本红线语义）。 */
  @PostMapping("/{ruleId}/hit-block")
  public ApiResponse<Void> setHitBlock(
      @PathVariable String ruleId, @RequestBody HitBlockRuleRequest request) {
    requireOwned(ruleQuery.findById(ruleId), ruleId);
    ruleService.setHitBlock(ruleId, request.hitBlock());
    return ApiResponse.success();
  }

  /** 废弃规则（不再参与求值；历史保留供回放）。 */
  @PostMapping("/{ruleId}/deprecate")
  public ApiResponse<Void> deprecate(@PathVariable String ruleId) {
    requireOwned(ruleQuery.findById(ruleId), ruleId);
    ruleService.deprecate(ruleId);
    return ApiResponse.success();
  }

  /** 试算求值：用给定上下文跑一次当前生效表达式（发布前验证 / 排障，不落任何状态）。 */
  @PostMapping("/{ruleId}/evaluate")
  public ApiResponse<Object> evaluate(
      @PathVariable String ruleId, @RequestBody EvaluateRuleRequest request) {
    requireOwned(ruleQuery.findById(ruleId), ruleId);
    return ApiResponse.success(
        ruleService.evaluate(ruleId, request.context(), request.trace()));
  }

  // ---- 租户校验：规则是租户隔离资产，跨租户读写一律显式拒绝 ----

  private RuleAsset requireOwned(Optional<RuleAsset> rule, String ruleId) {
    RuleAsset found =
        rule.orElseThrow(
            () -> new BizException(404, "rule not found: " + ruleId, AiRulesErrorCodes.NOT_FOUND));
    String tenantId = tenantContext.requireTenantId();
    if (!tenantId.equals(found.getTenantId())) {
      throw new BizException(403, "规则不属于当前租户: " + ruleId, AiRulesErrorCodes.NOT_FOUND);
    }
    return found;
  }

  public record CreateRuleRequest(String code, String name, String description) {}

  public record PublishRuleRequest(
      String expression, boolean hitBlock, int grayPercent, String operator) {}

  public record RollbackRuleRequest(int targetVersionNo, String operator) {}

  public record GrayRuleRequest(int grayPercent) {}

  public record HitBlockRuleRequest(boolean hitBlock) {}

  public record EvaluateRuleRequest(Map<String, Object> context, boolean trace) {}
}
