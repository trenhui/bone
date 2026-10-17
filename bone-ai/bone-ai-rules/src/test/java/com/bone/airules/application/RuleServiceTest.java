package com.bone.airules.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.airules.domain.gateway.RuleEngine;
import com.bone.airules.domain.model.RegressionCase;
import com.bone.airules.domain.model.RuleAsset;
import com.bone.airules.domain.model.RuleAssetVersion;
import com.bone.airules.domain.model.RuleEvalResult;
import com.bone.airules.domain.repository.RuleAssetRepository;
import com.bone.airules.domain.repository.RuleAssetVersionRepository;
import com.bone.core.exception.BizException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** RuleApplicationService：创建 / 首版发布 / 新版本 / 命中标记短路 / 灰度。 */
class RuleServiceTest {

  private RuleAssetRepository repository;
  private RuleAssetVersionRepository versionRepository;
  private RuleEngine ruleEngine;
  private RuleApplicationService service;

  @BeforeEach
  void setUp() {
    repository = mock(RuleAssetRepository.class);
    versionRepository = mock(RuleAssetVersionRepository.class);
    // 内存版版本仓储：save 累积、findByRuleId 回放、findByRuleIdAndVersionNo 检索、update 替换
    List<RuleAssetVersion> store = new java.util.ArrayList<>();
    when(versionRepository.save(any()))
        .thenAnswer(
            i -> {
              store.add(i.getArgument(0));
              return null;
            });
    when(versionRepository.update(any()))
        .thenAnswer(
            i -> {
              RuleAssetVersion v = i.getArgument(0);
              for (int idx = 0; idx < store.size(); idx++) {
                if (store.get(idx).getId().equals(v.getId())) {
                  store.set(idx, v);
                }
              }
              return null;
            });
    when(versionRepository.findByRuleId(anyString()))
        .thenAnswer(
            i -> {
              java.util.ArrayList<RuleAssetVersion> copy = new java.util.ArrayList<>(store);
              copy.sort(
                  java.util.Comparator.comparingInt(RuleAssetVersion::getVersionNo).reversed());
              return copy;
            });
    when(versionRepository.findByRuleIdAndVersionNo(anyString(), anyInt()))
        .thenAnswer(
            i ->
                store.stream()
                    .filter(v -> v.getVersionNo() == i.<Integer>getArgument(1))
                    .findFirst());
    ruleEngine = mock(RuleEngine.class);
    service = new RuleApplicationService(repository, versionRepository, ruleEngine);
    when(repository.findByCode("t1", "R01")).thenReturn(Optional.empty());
    // 智能应答：按表达式与 context.amount 决定结果（覆盖 precheck 空上下文与评估请求上下文）
    when(ruleEngine.evaluate(anyString(), anyMap(), anyBoolean()))
        .thenAnswer(
            inv -> {
              String expr = inv.getArgument(0);
              if ("bad (".equals(expr)) {
                return RuleEngine.RuleEvaluation.error("parse error", 1);
              }
              Map<String, Object> ctx = inv.getArgument(1);
              double amount = ((Number) ctx.getOrDefault("amount", 0)).doubleValue();
              return RuleEngine.RuleEvaluation.of(
                  "amount >= 10000".equals(expr) && amount >= 10000, "trace", 5);
            });
  }

  private RuleAsset createdRule() {
    RuleAsset rule = RuleAsset.create("t1", "R01", "大额风险", "金额≥1万");
    rule.setId("id-1");
    when(repository.findById("id-1")).thenReturn(rule);
    return rule;
  }

  @Test
  void create_and_publish_first() {
    RuleAsset rule = service.create("t1", "R01", "大额风险", "金额≥1万");
    assertThat(rule.getStatus().name()).isEqualTo("DRAFT");
    when(repository.findById(rule.getId())).thenReturn(rule);

    RuleAssetVersion v = service.publishFirst(rule.getId(), "amount >= 10000", true, 0, "ops");
    assertThat(v.getVersionNo()).isEqualTo(1);
    assertThat(rule.getStatus().name()).isEqualTo("ACTIVE");
    assertThat(rule.isHitBlock()).isTrue();
    assertThat(rule.getExpression()).isEqualTo("amount >= 10000");
  }

  @Test
  void publish_next_increments_version() {
    RuleAsset rule = createdRule();
    service.publishFirst(rule.getId(), "amount >= 10000", false, 0, "ops");
    RuleAssetVersion v2 = service.publishNext(rule.getId(), "amount >= 5000", true, 0, "ops");
    assertThat(v2.getVersionNo()).isEqualTo(2);
    assertThat(rule.getVersions()).hasSize(2);
    assertThat(rule.latestVersion().getVersionNo()).isEqualTo(2);
  }

  @Test
  void reject_duplicate_code() {
    when(repository.findByCode("t1", "R01"))
        .thenReturn(Optional.of(RuleAsset.create("t1", "R01", "n", "d")));
    assertThatThrownBy(() -> service.create("t1", "R01", "n", "d"))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("already exists")
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode()).isEqualTo("AI_RULE_CODE_DUPLICATED"));
  }

  @Test
  void reject_bad_expression_at_publish() {
    RuleAsset rule = createdRule();
    assertThatThrownBy(() -> service.publishFirst(rule.getId(), "bad (", false, 0, "ops"))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("compile failed")
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo("AI_RULE_EXPRESSION_INVALID"));
  }

  @Test
  void hit_with_block_short_circuits_llm() {
    RuleAsset rule = createdRule();
    service.publishFirst(rule.getId(), "amount >= 10000", true, 0, "ops");
    RuleEvalResult result =
        service.evaluate(rule.getId(), Map.of("amount", 15000.0, "traceId", "t"), true);
    assertThat(result.hit()).isTrue();
    assertThat(result.shouldBlockLlm()).isTrue();
    assertThat(result.shortCircuit()).isEqualTo("RULE_HIT_BLOCK");
  }

  @Test
  void hit_without_block_does_not_short_circuit() {
    RuleAsset rule = createdRule();
    service.publishFirst(rule.getId(), "amount >= 10000", false, 0, "ops");
    RuleEvalResult result =
        service.evaluate(rule.getId(), Map.of("amount", 15000.0, "traceId", "t"), false);
    assertThat(result.hit()).isTrue();
    assertThat(result.shouldBlockLlm()).isFalse();
  }

  @Test
  void inactive_rule_misses() {
    RuleAsset rule = createdRule();
    service.publishFirst(rule.getId(), "amount >= 10000", false, 0, "ops");
    rule.deprecate();
    RuleEvalResult result = service.evaluate(rule.getId(), Map.of("amount", 15000.0), false);
    assertThat(result.hit()).isFalse();
    assertThat(result.tracePath()).contains("NOT_ACTIVE");
  }

  @Test
  void gray_bucket_out_misses() {
    RuleAsset rule = createdRule();
    service.publishFirst(rule.getId(), "amount >= 10000", false, 1, "ops"); // 1% 灰度
    RuleEvalResult result =
        service.evaluate(rule.getId(), Map.of("amount", 15000.0, "traceId", "t-1"), false);
    assertThat(result.hit()).isFalse();
    assertThat(result.tracePath()).isEqualTo("GRAY_OUT");
  }

  @Test
  void evaluate_all_active_rules() {
    RuleAsset rule = createdRule();
    service.publishFirst(rule.getId(), "amount >= 10000", false, 0, "ops");
    when(repository.findActive("t1")).thenReturn(List.of(rule));
    List<RuleEvalResult> results = service.evaluateAll("t1", Map.of("amount", 15000.0), false);
    assertThat(results).hasSize(1);
    assertThat(results.get(0).hit()).isTrue();
  }

  @Test
  void publish_first_passes_when_regression_cases_match() {
    RuleAsset rule = createdRule();
    RuleAssetVersion v =
        service.publishFirst(
            rule.getId(),
            "amount >= 10000",
            true,
            0,
            "ops",
            List.of(
                RegressionCase.positive(Map.of("amount", 20000), "大额应命中"),
                RegressionCase.negative(Map.of("amount", 100), "小额不应命中")));
    assertThat(v.getVersionNo()).isEqualTo(1);
    assertThat(rule.getStatus().name()).isEqualTo("ACTIVE");
  }

  @Test
  void publish_first_rejects_when_regression_case_fails() {
    RuleAsset rule = createdRule();
    // 负例样本要求小额不命中，但表达式 amount>=10000 对 15000 命中 → 拒绝发布
    assertThatThrownBy(
            () ->
                service.publishFirst(
                    rule.getId(),
                    "amount >= 10000",
                    false,
                    0,
                    "ops",
                    List.of(RegressionCase.negative(Map.of("amount", 15000), "小额不应命中"))))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("regression failed")
        .hasMessageContaining("小额不应命中")
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo("AI_RULE_REGRESSION_FAILED"));
    assertThat(rule.getStatus().name()).isEqualTo("DRAFT"); // 拒绝发布，状态不变
  }

  @Test
  void publish_persists_version_and_rollback_publishes_new_version() {
    RuleAsset rule = createdRule();
    RuleAssetVersion v1 = service.publishFirst(rule.getId(), "amount >= 10000", false, 0, "ops");
    RuleAssetVersion v2 = service.publishNext(rule.getId(), "amount >= 5000", false, 0, "ops");
    // 版本链落库：publishFirst / publishNext 各自落一行
    verify(versionRepository, times(2)).save(any(RuleAssetVersion.class));
    assertThat(v1.getVersionNo()).isEqualTo(1);
    assertThat(v2.getVersionNo()).isEqualTo(2);
    assertThat(rule.getCurrentVersionNo()).isEqualTo(2);

    // 回滚到 v1：以历史表达式发布新版本 v3，旧当前版 v2 标记 ROLLED_BACK（不历史篡改）
    RuleAssetVersion rolled = service.rollback(rule.getId(), 1, "ops");
    assertThat(rolled.getVersionNo()).isEqualTo(3);
    assertThat(rolled.getExpression()).isEqualTo("amount >= 10000");
    // 历史链最新在前：v3 > v2(ROLLED_BACK) > v1，且 v2 被标记回滚
    List<RuleAssetVersion> history = service.history(rule.getId());
    assertThat(history.get(0).getVersionNo()).isEqualTo(3);
    assertThat(
            history.stream()
                .filter(h -> h.getVersionNo() == 2)
                .findFirst()
                .orElseThrow()
                .getStatus())
        .isEqualTo(RuleAssetVersion.STATUS_ROLLED_BACK);
  }
}
