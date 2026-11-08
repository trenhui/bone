package com.bone.claim.infrastructure.rule;

import com.bone.airules.domain.gateway.RuleEngine;
import com.bone.airules.infrastructure.engine.QlExpressRuleEngine;
import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.RuleEnginePort;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.config.ClaimThresholds;
import com.bone.claim.domain.model.evidence.Evidence;
import com.bone.claim.domain.model.evidence.EvidenceDateParser;
import com.bone.claim.domain.model.evidence.valueobject.OcrStatus;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.EvidenceRepository;
import com.bone.claim.domain.repository.LiabilityRepository;
import com.bone.claim.infrastructure.evidence.EvidenceFieldReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
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
 * <p><b>规则资产化（v2）</b>：规则集从代码常量迁到 {@code claim-ruleset.json}，可由 {@code bone.claim.ruleset.location}
 * 指向外部文件替换，改规则不再需要改 Java / 发版；版本号随每条命中明细落 {@code
 * t_claim_rule_hit.rule_version}，保证回放能回到当时那套规则。配置文件缺失、损坏或为空时回退内置默认集 （fail-closed 方向：不可信配置不生效），并保留
 * {@link #RULE_VERSION} 作回退版本。DB 热更 / 灰度 / 回归门禁仍属 bone-ai C2 RuleAsset 的 Phase B 范围，本类不冒充已具备。
 */
@Component
@ConditionalOnProperty(
    name = "bone.claim.rule-engine.mode",
    havingValue = "ql",
    matchIfMissing = true)
public class QlExpressRuleEnginePortAdapter implements RuleEnginePort {

  /** 内置回退规则集版本：改动任一表达式 / 结论码必须同步 bump，否则回放无法回到当时那套规则。 */
  static final String RULE_VERSION = "claim-ruleset-v1";

  /** 规则路径置信度：确定性规则的语义是「已执行且结论唯一」，不是统计置信度；沿用一期基线值， 不引入虚假校准。 */
  private static final BigDecimal RULE_CONFIDENCE = new BigDecimal("0.90");

  /** 业务时区（与 {@code ReconciliationApplicationService.BIZ_ZONE} 同口径）：日期比较必须按业务日，不按 UTC。 */
  private static final ZoneId BIZ_ZONE = ZoneId.of("Asia/Shanghai");

  /** 开票晚于就诊的最大容忍天数（R-FRAUD-03）：超过即「事后补票」形态，0 会把跨周末的正常补录全部打成风险。 */
  private static final int TIMELINE_MAX_LAG_DAYS = 30;

  /** 超保额告警比例（R-FRAUD-02 子面一，业务规则书口径 80%）。 */
  private static final BigDecimal COVERAGE_ALERT_RATIO = new BigDecimal("0.80");

  /** 3σ 判定的最小样本量（R-FRAUD-02 子面二，业务裁定要求写进规则书；n<30 时 σ 不可靠）。 */
  private static final int SIGMA_MIN_SAMPLES = 30;

  /** 3σ 倍数（单侧上界：只抓「明显偏高」，不抓偏低——偏低是少赔，不是反欺诈关注点）。 */
  private static final double SIGMA_MULTIPLIER = 3.0;

  private static final List<RuleSpec> DEFAULT_RULES =
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

  /**
   * 可选仓储富化：Spring 上下文装配后可用，按 {@code claimId} 拉取案卷/证据以构造「30+ 风控-合规-临床」维度变量。 用 {@code required=false}
   * 保证单元夹具以 {@code new} 创建（无 Spring 注入）时本适配器仍可独立运行， 此时 {@link #factsOf} 自动降级为基础 6
   * 维，规则按「变量缺失→静默未命中」处理，既有测试零改动。
   */
  @Autowired(required = false)
  private ClaimCaseRepository claimCaseRepository;

  @Autowired(required = false)
  private EvidenceRepository evidenceRepository;

  /**
   * 可选责任配置仓储：{@code R-AMOUNT-07}（超保额 80%）取责任限额用。
   *
   * <p>{@code required=false} 的原因与上面两个仓储相同——单元夹具以 {@code new} 创建适配器时无 Spring 注入，
   * 此时「保额」维度的变量缺失，规则按未命中处理，既有测试零改动。生产上下文里该仓储一定存在（本模块自有仓储）。
   */
  @Autowired(required = false)
  private LiabilityRepository liabilityRepository;

  @Value("${bone.claim.ruleset.location:classpath:claim-ruleset.json}")
  private Resource rulesetResource;

  private volatile boolean resolved;
  private ClaimRuleSet cached;

  @Override
  public RuleCheckResult check(ClaimContextPort.ClaimContextView ctx) {
    Map<String, Object> facts = factsOf(ctx);
    String snapshot = snapshotOf(facts);
    List<RuleHit> hits = new ArrayList<>();
    boolean riskHit = false;
    for (RuleSpec rule : rules()) {
      RuleEngine.RuleEvaluation evaluation = engine.evaluate(rule.expression(), facts, false);
      // 求值异常按未命中处理由 QlExpressRuleEngine 负责；此处只采信 hit=true 的确定性结论
      if (evaluation.hit()) {
        hits.add(
            new RuleHit(
                rule.code(),
                ruleVersion(),
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

  /** 配置不可用 → 内置默认集；无 Spring 装配的单元夹具同样走这条回退。 */
  private List<RuleSpec> rules() {
    ClaimRuleSet set = ruleSet();
    if (set == null) {
      return DEFAULT_RULES;
    }
    return set.rules().stream()
        .map(
            r ->
                new RuleSpec(
                    r.code(), r.category(), r.priority(), r.expression(), r.conclusion(), r.risk()))
        .toList();
  }

  private String ruleVersion() {
    ClaimRuleSet set = ruleSet();
    return set == null ? RULE_VERSION : set.version();
  }

  private ClaimRuleSet ruleSet() {
    if (!resolved) {
      synchronized (this) {
        if (!resolved) {
          cached = loadRuleSet();
          resolved = true;
        }
      }
    }
    return cached;
  }

  private ClaimRuleSet loadRuleSet() {
    if (rulesetResource == null) {
      return null;
    }
    try (InputStream in = rulesetResource.getInputStream()) {
      ClaimRuleSet set = mapper.readValue(in, ClaimRuleSet.class);
      if (set == null
          || set.version() == null
          || set.version().isBlank()
          || set.rules() == null
          || set.rules().isEmpty()) {
        return null;
      }
      return set;
    } catch (Exception ex) {
      return null;
    }
  }

  /**
   * 只读上下文 → 规则求值变量；数值统一转 double，避免 BigDecimal 与 DSL 字面量比较的类型歧义。
   *
   * <p>基础 6 维来自 {@link ClaimContextPort.ClaimContextView}（单元测试直接构造时即为全部可用变量）； 当 Spring 装配了领域仓储时，额外按
   * {@code claimId} 富化案卷/证据维度的「30+ 风控-合规-临床」变量， 使规则集能覆盖图 1/图 2 描述的智能核责维度。单元测试以 {@code new}
   * 创建适配器（仓储为 null）时自动降级为基础 6 维，测试零改动。
   */
  private Map<String, Object> factsOf(ClaimContextPort.ClaimContextView ctx) {
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
    enrichFromRepositories(ctx, facts);
    return facts;
  }

  /**
   * 仓储就绪时富化案卷/证据维度变量；任何异常均降级为基础 facts（best-effort，不阻断主链路）。 仅取实体列字段（{@code items} 为
   * {@code @Transient} 且 {@code findById} 不回填，故不在此消费）， 避免误判「无明细」而误伤正常案件。
   */
  private void enrichFromRepositories(
      ClaimContextPort.ClaimContextView ctx, Map<String, Object> facts) {
    if (claimCaseRepository == null || evidenceRepository == null) {
      return;
    }
    try {
      ClaimCase claim = claimCaseRepository.findById(ctx.claimId());
      List<Evidence> evidences = evidenceRepository.findByClaimId(ctx.claimId());
      boolean hasClaim = claim != null;
      facts.put("policyNoBlank", isBlank(hasClaim ? claim.getPolicyNo() : null));
      facts.put("insuredIdHashBlank", isBlank(hasClaim ? claim.getInsuredIdHash() : null));
      facts.put("hospitalCodeBlank", isBlank(hasClaim ? claim.getHospitalCode() : null));
      facts.put("stdHospitalCodeBlank", isBlank(hasClaim ? claim.getStdHospitalCode() : null));
      facts.put("diagnosisCodeBlank", isBlank(hasClaim ? claim.getDiagnosisCode() : null));
      facts.put("stdDiagnosisCodeBlank", isBlank(hasClaim ? claim.getStdDiagnosisCode() : null));
      facts.put("mappingVersionBlank", isBlank(hasClaim ? claim.getMappingVersion() : null));
      facts.put("consentWithdrawn", hasClaim && claim.consentWithdrawn());
      facts.put("manualReviewRequired", hasClaim && claim.isManualReviewRequired());
      facts.put(
          "secondReviewRequired",
          hasClaim && claim.requiresSecondReview(ClaimThresholds.DUAL_REVIEW_AMOUNT));
      facts.put("claimTypeOutpatient", "OUTPATIENT".equals(hasClaim ? claim.getClaimType() : null));
      facts.put("outInsureTimeMissing", hasClaim && claim.getOutInsureTime() == null);
      facts.put("createdAtMissing", hasClaim && claim.getCreatedAt() == null);
      facts.put(
          "sourceChannel",
          hasClaim && claim.getSourceChannel() != null ? claim.getSourceChannel() : "");
      List<ClaimCase> priorClaims = enrichPriorClaimFacts(claim, ctx, facts);
      enrichTimelineFacts(claim, evidences, facts);
      enrichAmountAnomalyFacts(claim, priorClaims, facts);
      enrichDuplicateFacts(claim, priorClaims, facts);
      int evidenceCount = evidences == null ? 0 : evidences.size();
      int invoiceCount = 0;
      boolean anyOcrFailed = false;
      if (evidences != null) {
        for (Evidence e : evidences) {
          if ("INVOICE".equals(e.getDocType())) {
            invoiceCount++;
          }
          if (e.ocrStatusEnum() == OcrStatus.FAILED) {
            anyOcrFailed = true;
          }
        }
      }
      facts.put("evidenceCount", evidenceCount);
      facts.put("invoiceEvidenceCount", invoiceCount);
      facts.put("anyEvidenceOcrFailed", anyOcrFailed);
    } catch (Exception ignored) {
      // 富化失败保持基础 facts，规则按「变量缺失→静默未命中」处理
    }
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  /**
   * 跨案历史维度富化（R-FRAUD-22 跨案重复索赔 / R-ANOM-03 近 90 天高频报案）。
   *
   * <p>按被保人假名化 ID 取同租户全部赔案，剔除本案自身（{@code ctx.claimId()}）后计数：
   *
   * <ul>
   *   <li>{@code priorClaimCount}：历史案件总数（排除本案），≥1 即触发 R-FRAUD-22；
   *   <li>{@code recentClaims90d}：近 90 天内新增案件数（排除本案），≥3 即触发 R-ANOM-03。
   * </ul>
   *
   * <p>剔除本案自身是关键：否则新报案会把自身算进历史、必然命中 R-FRAUD-22，造成 100% 误报。任一异常（仓储未装配 / 查询返回 null / 字段缺失）均
   * best-effort 降级为空集合；规则按「变量缺失→静默未命中」处理，绝不阻断主链路。
   */
  private List<ClaimCase> enrichPriorClaimFacts(
      ClaimCase claim, ClaimContextPort.ClaimContextView ctx, Map<String, Object> facts) {
    if (claim == null || claim.getInsuredIdHash() == null) {
      facts.put("priorClaimCount", 0.0);
      facts.put("recentClaims90d", 0.0);
      return List.of();
    }
    List<ClaimCase> priorClaims =
        claimCaseRepository.findByInsuredIdHash(claim.getTenantId(), claim.getInsuredIdHash());
    if (priorClaims == null) {
      priorClaims = List.of();
    }
    // 排除本案自身：不排除会让新报案把自己算进历史，R-FRAUD-22 / 重复报销判定 100% 误报。
    List<ClaimCase> others =
        priorClaims.stream()
            .filter(p -> p.getId() != null && !p.getId().equals(ctx.claimId()))
            .toList();
    Instant ninetyDaysAgo = Instant.now().minus(90, ChronoUnit.DAYS);
    long recentClaims90d =
        others.stream()
            .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().isAfter(ninetyDaysAgo))
            .count();
    facts.put("priorClaimCount", (double) others.size());
    facts.put("recentClaims90d", (double) recentClaims90d);
    return others;
  }

  /**
   * 时间轴矛盾维度富化（R-FRAUD-03 的实际承载，2026-11-08 按业务裁定 A 补实现）。
   *
   * <p><b>为什么需要两个日期来源</b>：{@code t_claim_case.invoice_date} 是建单时录入的实体列，
   * 最可靠；但材料阶段刚解析完、案件还没回填时它可能为空。此时回落到票据 {@code extracted_json} 的 {@code invoice_date} 字段，再经 {@link
   * EvidenceDateParser} 的白名单解析——<b>认不出就 {@code timelineUnresolved=true}
   * 转人工，绝不猜</b>（猜错会把正常案件判成「药先开票」）。
   *
   * <p>未接线时（无仓储 / 无案件）三个布尔都置 false 而非 true：把「拿不到数据」当成矛盾会让整批案件转人工， 那是把数据问题当业务结论。
   */
  private void enrichTimelineFacts(
      ClaimCase claim, List<Evidence> evidences, Map<String, Object> facts) {
    if (claim == null) {
      facts.put("timelineUnresolved", false);
      facts.put("invoiceBeforeVisit", false);
      facts.put("invoiceTooLate", false);
      facts.put("invoiceVisitLagDays", 0.0);
      return;
    }
    LocalDate invoiceDate = resolveInvoiceDate(claim, evidences);
    LocalDate visitDate = resolveVisitDate(claim);
    EvidenceDateParser.TimelineVerdict verdict =
        EvidenceDateParser.evaluate(invoiceDate, visitDate, TIMELINE_MAX_LAG_DAYS);
    facts.put("timelineUnresolved", verdict == EvidenceDateParser.TimelineVerdict.UNRESOLVED);
    facts.put(
        "invoiceBeforeVisit", verdict == EvidenceDateParser.TimelineVerdict.INVOICE_BEFORE_VISIT);
    facts.put("invoiceTooLate", verdict == EvidenceDateParser.TimelineVerdict.INVOICE_TOO_LATE);
    facts.put(
        "invoiceVisitLagDays",
        invoiceDate != null && visitDate != null
            ? (double) ChronoUnit.DAYS.between(visitDate, invoiceDate)
            : 0.0);
  }

  /**
   * 发票日期：实体列优先，缺失时回落票据解析产物。
   *
   * <p>回落只认 {@code INVOICE} 类型证据里的 {@code invoice_date} 字段；多张票据时取<b>第一条能解析出的</b>——
   * 一份赔案多张发票的情形下「哪张是主票」本身没有可靠依据，取首条解析成功值并保留「可能多票」的既有语义差异， 好过为此引入一个猜测性的排序规则。
   */
  private LocalDate resolveInvoiceDate(ClaimCase claim, List<Evidence> evidences) {
    if (claim.getInvoiceDate() != null) {
      return claim.getInvoiceDate();
    }
    if (evidences == null) {
      return null;
    }
    for (Evidence evidence : evidences) {
      if (!"INVOICE".equals(evidence.getDocType())) {
        continue;
      }
      for (EvidenceFieldReader.ParsedField field :
          EvidenceFieldReader.read(evidence.getExtractedJson())) {
        if ("invoice_date".equals(field.code())) {
          var parsed = EvidenceDateParser.parse(field.value());
          if (parsed.isPresent()) {
            return parsed.get();
          }
        }
      }
    }
    return null;
  }

  /**
   * 就诊日期：取 {@code out_insure_time}（出险时刻）在业务时区下的日历日。
   *
   * <p>必须按 {@link #BIZ_ZONE} 转日再比较：直接 {@code toLocalDate()} 用的是 UTC，跨零点开票的票会整体错一天， 把「同日」判成「次日」。
   */
  private LocalDate resolveVisitDate(ClaimCase claim) {
    if (claim.getOutInsureTime() == null) {
      return null;
    }
    return claim.getOutInsureTime().atZone(BIZ_ZONE).toLocalDate();
  }

  /**
   * 金额异常维度富化（R-FRAUD-02 的两个子面，2026-11-08 按业务裁定 B 全部补实现）。
   *
   * <p><b>子面一：超保额 80%</b>——取计划下生效责任的 {@code liability_limit} 最大值作为计划级上限。
   * 这是<b>计划级</b>而不是<b>案件级</b>限额：案件表没有 coverageId / 责任 UUID，无法定位「这笔费用属于哪条责任」。
   * 因此本判定只会漏报（超过小责任限额但没超过计划上限时不触发），不会误报——方向是安全的， 精确到责任级需要案件携带责任标识，属接线项而非本判据缺陷。
   *
   * <p><b>子面二：同类 3σ</b>——样本取「同被保人 + 同诊断码 + 金额非空」的历史案件。 三条闸门缺一不可：① 样本量 {@code >=
   * SIGMA_MIN_SAMPLES}（默认 30，业务裁定要求写进规则书）； ② 标准差 {@code > 0}（全同值样本没有分布，硬算会除零，也会把「所有历史都一样」误判成异常）； ③
   * 诊断码非空（空码无法定义「同类」）。 任一不满足即 {@code amountSigmaReady=false}，规则不判——<b>宁可不判，不拿不可靠统计误杀大额正常赔付</b>。
   */
  private void enrichAmountAnomalyFacts(
      ClaimCase claim, List<ClaimCase> priorClaims, Map<String, Object> facts) {
    BigDecimal amount = claim == null ? null : claim.getClaimAmount();
    BigDecimal planLimit = resolvePlanLiabilityLimit(claim);
    boolean coverageKnown = planLimit != null && planLimit.signum() > 0 && amount != null;
    facts.put("coverageLimitKnown", coverageKnown);
    facts.put(
        "amountOverCoverage80",
        coverageKnown && amount.compareTo(planLimit.multiply(COVERAGE_ALERT_RATIO)) >= 0);

    String diagnosisCode = claim == null ? "" : trimToEmpty(claim.getDiagnosisCode());
    List<BigDecimal> samples =
        priorClaims.stream()
            .filter(p -> diagnosisCode.equals(trimToEmpty(p.getDiagnosisCode())))
            .map(ClaimCase::getClaimAmount)
            .filter(java.util.Objects::nonNull)
            .toList();
    boolean enough =
        amount != null && !diagnosisCode.isEmpty() && samples.size() >= SIGMA_MIN_SAMPLES;
    double sigma = enough ? standardDeviation(samples) : 0.0;
    boolean ready = enough && sigma > 0.0;
    facts.put("amountSigmaReady", ready);
    facts.put("amountSigmaSampleSize", (double) samples.size());
    facts.put(
        "amountSigmaOutlier",
        ready && amount.doubleValue() > mean(samples) + SIGMA_MULTIPLIER * sigma);
  }

  /**
   * 计划级责任限额：生效责任里 {@code liability_limit} 的最大值。
   *
   * <p>返回 {@code null} 表示「不可得」（无仓储 / 无 planId / 全部责任限额为 0），调用方据此不判—— 把「没配限额」当成「限额为
   * 0」会让每一笔都命中超保额，是典型的静默灾难。
   */
  private BigDecimal resolvePlanLiabilityLimit(ClaimCase claim) {
    if (claim == null || claim.getPlanId() == null || liabilityRepository == null) {
      return null;
    }
    try {
      List<AdjLiability> liabilities = liabilityRepository.findActiveByPlanId(claim.getPlanId());
      if (liabilities == null || liabilities.isEmpty()) {
        return null;
      }
      return liabilities.stream()
          .map(AdjLiability::getLiabilityLimit)
          .filter(java.util.Objects::nonNull)
          .filter(v -> v.signum() > 0)
          .max(BigDecimal::compareTo)
          .orElse(null);
    } catch (Exception ignored) {
      // 责任配置读取失败 → 保额不可得 → 不判（不阻断主链路，也不假装限额为 0）
      return null;
    }
  }

  /** 样本标准差（n-1 分母；样本量 < 2 返回 0，由调用方按「不可判」处理）。 */
  private static double standardDeviation(List<BigDecimal> values) {
    if (values.size() < 2) {
      return 0.0;
    }
    double avg = mean(values);
    double sum = 0.0;
    for (BigDecimal value : values) {
      double diff = value.doubleValue() - avg;
      sum += diff * diff;
    }
    return Math.sqrt(sum / (values.size() - 1));
  }

  private static double mean(List<BigDecimal> values) {
    return values.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0.0);
  }

  /**
   * 重复报销维度富化（R-FRAUD-01 的独立判定，2026-11-08 按业务裁定补实现）。
   *
   * <p>四要素：<b>同人</b>（{@code findByInsuredIdHash} 已保证）+ <b>同日</b>（发票日期相同）+ <b>同院</b>（医院编码相同）+
   * <b>同额</b>（理赔金额相同）。四者齐备才算命中，缺一项即不命中。
   *
   * <p><b>为什么「缺要素」不直接转人工</b>：医院编码缺失由 {@code R-FRAUD-08 HOSPITAL_MISSING} 单独转人工，
   * 这里再转一次等于同一问题报两次；而发票日期缺失、且库里确实存在「同院同额」的候选时—— 差的就是这一天——才置 {@code duplicateUnresolved=true}
   * 交人工确认。这样既守住 fail-closed， 又不会因为材料还没回填就把整批案件打成待人工。
   *
   * <p>金额比较用 {@link BigDecimal#compareTo}：{@code 100} 与 {@code 100.00} 数值相等， 用 {@code equals}
   * 会把精度差异当成不同金额，直接漏掉重复。
   */
  private void enrichDuplicateFacts(
      ClaimCase claim, List<ClaimCase> priorClaims, Map<String, Object> facts) {
    BigDecimal amount = claim == null ? null : claim.getClaimAmount();
    String hospital = claim == null ? "" : trimToEmpty(claim.getHospitalCode());
    LocalDate invoiceDate = claim == null ? null : claim.getInvoiceDate();
    if (claim == null || priorClaims.isEmpty() || amount == null || hospital.isEmpty()) {
      facts.put("duplicateReimbursement", false);
      facts.put("duplicateUnresolved", false);
      facts.put("duplicateCandidateCount", 0.0);
      return;
    }
    List<ClaimCase> nearCandidates =
        priorClaims.stream()
            .filter(p -> hospital.equals(trimToEmpty(p.getHospitalCode())))
            .filter(p -> p.getClaimAmount() != null && p.getClaimAmount().compareTo(amount) == 0)
            .toList();
    boolean sameDay =
        invoiceDate == null
            ? false
            : nearCandidates.stream().anyMatch(p -> invoiceDate.equals(p.getInvoiceDate()));
    facts.put("duplicateReimbursement", sameDay);
    facts.put("duplicateUnresolved", invoiceDate == null && !nearCandidates.isEmpty());
    facts.put("duplicateCandidateCount", (double) nearCandidates.size());
  }

  private static String trimToEmpty(String value) {
    return value == null ? "" : value.trim();
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
