package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.config.AiRolloutBudget;
import com.bone.claim.domain.model.config.AiRolloutPolicy;
import com.bone.claim.domain.model.config.AiRolloutStage;
import com.bone.claim.domain.model.config.FeatureToggle;
import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.claim.domain.model.inspection.InspectionRecord;
import com.bone.claim.domain.repository.DecisionExecutionRepository;
import com.bone.claim.domain.repository.FeatureToggleRepository;
import com.bone.claim.domain.repository.InspectionRepository;
import com.bone.core.annotation.NoDomainEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 放量阶段机与错误预算熔断（G3-1 / G3-2）。
 *
 * <p><b>补的是什么洞</b>：既有实现里「自动放款」只有三道闸——全局开关、租户开关（含 {@code rollout_pct}）、单笔金额上限。 这意味着运维<b>改一行 SQL
 * 就能从「完全不放量」跳到「全量放款」</b>，中间没有任何「先小流量、看效果、再放大」的机制； 也没有任何东西会在事后质量变差时自动收紧。这两条正是 G3
 * 要补的：一个阶段机（放得慢）加一个预算熔断器（出问题自己停）。
 *
 * <p><b>为什么不新增表</b>：阶段与阈值都是 {@code feature_toggle.config_json} 里的键（该列本就是为「按特性不同而不同的
 * 附带参数」准备的），回滚现场写回同一行的 {@code remark} 与 {@code config_json}。这样零 DDL、零 L3 审批，且
 * 「谁把档位降了、依据什么」与开关本身同生共死——不会出现「开关表说 AUTOMATED、审计表说昨天降过档」的分叉。
 *
 * <p><b>为什么在调用时判断而不是起定时任务</b>：熔断的价值在于「下一次出金之前拦住」。定时任务有轮询间隔， 而这里每次自动放款前用一次有界查询（≤500
 * 行）换「即时生效」，代价可接受——更何况该分支只在「已通过全局开关 + 租户开关 + 结论为 AUTO_APPROVE + 未结案 + 未超上限」之后才到达，频率极低。
 *
 * <p><b>E-5.4 豁免</b>（{@link NoDomainEvent}）：本类唯一的写入是「把 {@code t_feature_toggle} 的参数行改小一档」，
 * 不推进任何业务聚合（案件 / 额度 / 结算）的状态，故不发布领域事件。这次降档的留痕走三条独立通道：① 同行的 {@code remark} 写明「从哪档降到哪档、依据什么指标」；②
 * {@code config_json.lastRollback} 保留击穿时的计数与比率快照； ③ ERROR
 * 级日志（告警管道可直采）。若日后配置变更需要进领域事件总线（例如统一推送到风控告警），应新增 {@code FeatureToggleChangedEvent}
 * 并移除本豁免，而不是在此处补一个语义含糊的事件。
 */
@Slf4j
@NoDomainEvent
@Service
@RequiredArgsConstructor
public class AiRolloutApplicationService {

  /** 预算取数上界（与仓储 SQL 的 LIMIT 500 对齐：打满即视为截断）。 */
  static final int WINDOW_ROW_LIMIT = 500;

  /** 快照查询窗口上界（与其他运维端点一致）。 */
  public static final int MAX_QUERY_DAYS = 31;

  private static final String REASON_STAGE_NOT_AUTOMATED_PREFIX = "AI_STAGE_";
  private static final String REASON_CANARY_MISS = "AI_CANARY_MISS";
  private static final String REASON_BUDGET_EXCEEDED = "AI_BUDGET_EXCEEDED_AUTO_ROLLBACK";

  private final FeatureToggleRepository featureToggleRepository;
  private final InspectionRepository inspectionRepository;
  private final DecisionExecutionRepository decisionExecutionRepository;
  private final TenantPort tenantPort;
  private final ObjectMapper objectMapper;

  /**
   * 放量准入结论。
   *
   * @param allowed 是否允许自动出金
   * @param reason 受控原因码（{@code allowed=true} 时为 {@code ADMITTED}）
   * @param stage 生效档位
   * @param errorRate 抽检口径错误率（无样本为 null，<b>不是 0</b>）
   * @param verdict 预算判据结论
   * @param autoRolledBack 本次调用是否触发了自动降档
   */
  public record Admission(
      boolean allowed,
      String reason,
      AiRolloutStage stage,
      BigDecimal errorRate,
      String verdict,
      boolean autoRolledBack) {}

  /**
   * 放量快照（运维只读）。
   *
   * @param promotionBlockers 阻止晋升的原因（空表示已具备晋升条件）
   */
  public record RolloutSnapshot(
      AiRolloutStage stage,
      int rolloutPct,
      BigDecimal errorRateTarget,
      int minInspectedSamples,
      int observationDays,
      int inspectedCount,
      int diffCount,
      BigDecimal errorRate,
      BigDecimal remainingRatio,
      boolean sampleSufficient,
      boolean truncated,
      String verdict,
      AiRolloutStage nextStage,
      boolean promotionReady,
      List<String> promotionBlockers,
      Map<String, String> rejectedConfig) {}

  /**
   * 判定该案件是否允许自动出金（阶段 → 分桶 → 错误预算，三层递增严格）。
   *
   * <p>任一层拒绝都<b>不触碰任何聚合</b>，只返回受控原因码。
   *
   * @param tenantId 租户
   * @param claimId 案件（分桶键；同一案件重复调用结论必须一致）
   * @return 准入结论，永不返回 null
   */
  @Transactional
  public Admission admit(long tenantId, long claimId) {
    Loaded loaded = load(tenantId);
    AiRolloutPolicy policy = loaded.policy;

    // 第 1 层：阶段闸。SHADOW / ASSISTED 即使上游全绿也不出金——这是「只观察」与「人工决策」两档的定义。
    if (!policy.stage().permitsAutoSettlement()) {
      return new Admission(
          false,
          REASON_STAGE_NOT_AUTOMATED_PREFIX + policy.stage().name(),
          policy.stage(),
          null,
          AiRolloutBudget.Verdict.NO_EVIDENCE.name(),
          false);
    }
    // 第 2 层：分桶闸。CANARY 下未命中的案件照旧走人工（幂等：同一 claimId 永远落在同一个桶）。
    if (!policy.admitsCase(tenantId, claimId)) {
      return new Admission(false, REASON_CANARY_MISS, policy.stage(), null, null, false);
    }
    // 第 3 层：错误预算。只有「证据充分且已超目标」才熔断；证据不足不熔断（冷启动必须能走通），
    // 但证据不足同样会阻止后续晋升（见 snapshot 的 promotionBlockers）。
    AiRolloutBudget budget = budgetOf(policy, policy.observationDays());
    if (budget.verdict().mustHalt()) {
      boolean rolledBack = autoRollback(loaded.toggle, policy, budget);
      log.warn(
          "[AI 放量] 错误预算击穿，已收紧：tenantId={}, claimId={}, inspected={}, diff={}, rate={}, target={}, rolledBack={}",
          tenantId,
          claimId,
          budget.inspectedCount(),
          budget.diffCount(),
          budget.errorRate(),
          budget.errorRateTarget(),
          rolledBack);
      return new Admission(
          false,
          REASON_BUDGET_EXCEEDED,
          policy.stage().previous(),
          budget.errorRate(),
          budget.verdict().name(),
          rolledBack);
    }
    return new Admission(
        true, "ADMITTED", policy.stage(), budget.errorRate(), budget.verdict().name(), false);
  }

  /**
   * 放量快照（<b>只读，不产生任何回滚副作用</b>）。
   *
   * <p><b>为什么快照与准入共用同一套口径</b>：如果运维看到的错误率与准入判据用的不是同一个数，就会出现「面板全绿但案件被 拦下」的排障死局。故两者都走 {@link
   * #budgetOf(long, int)}。
   *
   * @param tenantId 租户
   * @param from 窗口起点（含）
   * @param to 窗口终点（不含）
   * @return 快照
   */
  public RolloutSnapshot snapshot(Instant from, Instant to) {
    return snapshot(tenantPort.currentTenantId(), from, to);
  }

  /**
   * 放量快照（显式租户版，供测试与内部调用）。
   *
   * @param tenantId 租户
   * @param from 窗口起点（含）
   * @param to 窗口终点（不含）
   * @return 快照
   */
  public RolloutSnapshot snapshot(long tenantId, Instant from, Instant to) {
    validateWindow(from, to);
    Loaded loaded = load(tenantId);
    AiRolloutPolicy policy = loaded.policy;
    AiRolloutBudget budget = budgetOf(policy, from, to);
    List<String> blockers = new ArrayList<>();
    if (policy.stage() == AiRolloutStage.AUTOMATED) {
      blockers.add("ALREADY_AUTOMATED");
    } else if (budget.verdict().blocksPromotion()) {
      blockers.add(budget.verdict().name());
    }
    return new RolloutSnapshot(
        policy.stage(),
        policy.rolloutPct(),
        policy.errorRateTarget(),
        policy.minInspectedSamples(),
        policy.observationDays(),
        budget.inspectedCount(),
        budget.diffCount(),
        budget.errorRate(),
        budget.remainingRatio(),
        budget.sampleSufficient(),
        budget.truncated(),
        budget.verdict().name(),
        policy.stage().next(),
        blockers.isEmpty(),
        List.copyOf(blockers),
        loaded.policyResolution.rejected());
  }

  // ===================== 内部 =====================

  /** 开关行 + 生效策略（一次读取，避免「读策略」与「写回滚」之间被别的线程改掉版本号）。 */
  private record Loaded(
      FeatureToggle toggle, AiRolloutPolicy policy, AiRolloutPolicy.Resolution policyResolution) {}

  private Loaded load(long tenantId) {
    if (featureToggleRepository == null) {
      // 防御：仓储未注入（纯单测基座 / 场景基座）时按「未配置」处理——SHADOW，不放量。
      // 与 FeatureTenantToggleService.triageLimits 的同类兜底一致：任何异常路径都不得把资金策略放宽。
      return new Loaded(null, AiRolloutPolicy.defaults(), AiRolloutPolicy.apply(Map.of()));
    }
    FeatureToggle toggle =
        featureToggleRepository.findByFeatureKey(tenantId, FeatureToggle.FEATURE_AUTO_APPROVE);
    if (toggle == null) {
      // 未配置 = 未签核放量 = SHADOW。这一条让「新租户默认全量自动放款」在结构上不可能发生。
      return new Loaded(null, AiRolloutPolicy.defaults(), AiRolloutPolicy.apply(Map.of()));
    }
    if (!toggle.isOn(tenantId)) {
      // 显式 DISABLED 或灰度未命中 ⇒ 视为 SHADOW（不放量）。这是运维的 kill switch，读库即时生效、无缓存。
      return new Loaded(toggle, AiRolloutPolicy.defaults(), AiRolloutPolicy.apply(Map.of()));
    }
    AiRolloutPolicy.Resolution resolution =
        AiRolloutPolicy.apply(readConfig(toggle.getConfigJson()), safePct(toggle.getRolloutPct()));
    if (!resolution.fullyApplied()) {
      log.warn(
          "[AI 放量] 配置部分未生效（其余项已生效）：tenantId={}, rejected={}",
          tenantId,
          resolution.rejectionSummary());
    }
    return new Loaded(toggle, resolution.policy(), resolution);
  }

  private AiRolloutBudget budgetOf(AiRolloutPolicy policy, int observationDays) {
    Instant to = Instant.now();
    Instant from = to.minus(Duration.ofDays(observationDays));
    return budgetOf(policy, from, to);
  }

  /**
   * 计算窗口内的错误预算。
   *
   * <p><b>分母只含「自动通过案件里已抽检并出结论」的单子</b>：抽检是抽样动作，用全部自动通过数当分母会让错误率被抽样比例 稀释（抽得越少、看起来越准）。分子是其中判为 {@code
   * DIFF} 的。
   *
   * <p><b>两次查询是一致性快照的近似</b>：先取窗口内收敛的决策执行，再取窗口内结论的抽检单，在内存里按 {@code claimId}
   * 求交——两步之间理论上可能插入新行，但预算判据本身是「趋势级」的，一次抽样偏差不会改变「是否已持续超限」的结论； 换成单个 JOIN 查询则需要给 SDK 加投影能力，收益不抵风险。
   */
  private AiRolloutBudget budgetOf(AiRolloutPolicy policy, Instant from, Instant to) {
    List<DecisionExecution> executions =
        decisionExecutionRepository.findConvergedInWindow(from, to);
    List<InspectionRecord> inspections = inspectionRepository.findConcludedInWindow(from, to);
    Set<Long> autoApprovedClaims = new HashSet<>();
    for (DecisionExecution execution : safe(executions)) {
      if ("AUTO_APPROVE".equals(execution.getDecisionResult()) && execution.getClaimId() != null) {
        autoApprovedClaims.add(execution.getClaimId());
      }
    }
    int inspected = 0;
    int diff = 0;
    for (InspectionRecord inspection : safe(inspections)) {
      if (inspection.getClaimId() == null
          || !autoApprovedClaims.contains(inspection.getClaimId())) {
        continue;
      }
      inspected++;
      if ("DIFF".equals(inspection.getResult())) {
        diff++;
      }
    }
    boolean truncated =
        safe(executions).size() >= WINDOW_ROW_LIMIT || safe(inspections).size() >= WINDOW_ROW_LIMIT;
    return new AiRolloutBudget(
        inspected, diff, truncated, policy.errorRateTarget(), policy.minInspectedSamples());
  }

  /**
   * 自动降一档并回写现场。
   *
   * <p><b>降一档而不是直接打回 SHADOW</b>：一次预算抖动若直接全停，业务侧会把它当成故障并立刻手动开回去； 降一档（AUTOMATED → CANARY →
   * ASSISTED）既缩小了敞口，又保留了「人工可见建议」的能力，更容易被接受。
   *
   * @return 是否真的写入（无开关行、已到最低档、写入失败都返回 false）
   */
  private boolean autoRollback(
      FeatureToggle toggle, AiRolloutPolicy policy, AiRolloutBudget budget) {
    if (toggle == null) {
      return false;
    }
    AiRolloutStage from = policy.stage();
    AiRolloutStage to = from.previous();
    if (to == from) {
      // 已在 ASSISTED（CANARY 的上一档）或更低：准入路径本就到不了这里，防御性返回。
      return false;
    }
    Instant now = Instant.now();
    Map<String, Object> config = readConfig(toggle.getConfigJson());
    Map<String, Object> rollback = new LinkedHashMap<>();
    rollback.put("at", now.toString());
    rollback.put("from", from.name());
    rollback.put("to", to.name());
    rollback.put("reason", "ERROR_BUDGET_EXCEEDED");
    rollback.put("inspectedCount", budget.inspectedCount());
    rollback.put("diffCount", budget.diffCount());
    rollback.put("errorRate", String.valueOf(budget.errorRate()));
    rollback.put("errorRateTarget", String.valueOf(budget.errorRateTarget()));
    config.put(AiRolloutPolicy.KEY_STAGE, to.name());
    config.put("lastRollback", rollback);
    config.put("rollbackCount", rollbackCount(config) + 1);
    try {
      toggle.overwriteConfigJson(
          objectMapper.writeValueAsString(config),
          "自动降档 "
              + from.name()
              + "→"
              + to.name()
              + "：错误预算击穿（抽检 "
              + budget.inspectedCount()
              + " 条中 "
              + budget.diffCount()
              + " 条差异，"
              + budget.errorRate()
              + " > "
              + budget.errorRateTarget()
              + "）",
          now);
      featureToggleRepository.save(toggle);
      log.error("[AI 放量] 自动降档已落库（这是需要人来看的告警级事件）：{} → {}，依据={}", from, to, rollback);
      return true;
    } catch (Exception e) {
      // 降档失败不能把业务调用拖挂：本次已经拒绝出金（安全方向已成立），下一案还会再试一次。
      // 但必须 ERROR 级别留痕——静默失败会让「熔断器从不生效」这件事无人知晓。
      log.error("[AI 放量] 自动降档写入失败（本次仍已拒绝出金，下次调用会重试）：{}", e.getMessage(), e);
      return false;
    }
  }

  private int rollbackCount(Map<String, Object> config) {
    Object value = config.get("rollbackCount");
    if (value instanceof Number number) {
      return number.intValue();
    }
    return 0;
  }

  /** 列值拆箱兜底：{@code rollout_pct} 是 NOT NULL 列，但历史行/直连写入仍可能为 null。 */
  private static int safePct(Integer pct) {
    return pct == null ? 0 : pct;
  }

  private Map<String, Object> readConfig(String raw) {
    if (raw == null || raw.isBlank()) {
      return new LinkedHashMap<>();
    }
    try {
      Map<String, Object> parsed =
          objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
      return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
    } catch (Exception e) {
      // 配置坏了也要能把档位降下来：用空 map 重建（其余键丢失会被 WARN 记录，但熔断不能被一段坏 JSON 卡住）。
      log.warn("[AI 放量] config_json 解析失败，回滚时按空配置重建：{}", e.getMessage());
      return new LinkedHashMap<>();
    }
  }

  private void validateWindow(Instant from, Instant to) {
    if (from == null || to == null || !from.isBefore(to)) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_AI_ROLLOUT_QUERY_INVALID, "时间窗缺失或起点不早于终点");
    }
    if (Duration.between(from, to).toDays() > MAX_QUERY_DAYS) {
      throw ClaimErrors.of(
          ClaimErrorCodes.CLAIM_AI_ROLLOUT_QUERY_INVALID, "窗口跨度不得超过 " + MAX_QUERY_DAYS + " 天");
    }
  }

  private static <T> List<T> safe(List<T> list) {
    return list == null ? List.of() : list;
  }
}
