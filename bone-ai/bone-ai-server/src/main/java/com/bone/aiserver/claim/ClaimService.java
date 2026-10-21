package com.bone.aiserver.claim;

import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.aidoc.domain.gateway.DocumentParser;
import com.bone.aidoc.domain.gateway.ParsedDocument;
import com.bone.aidoc.domain.model.DocumentClause;
import com.bone.aidoc.domain.model.DocumentIR;
import com.bone.aidoc.domain.service.ClauseSplitter;
import com.bone.ailedger.domain.gateway.LimitLedger;
import com.bone.ailedger.domain.model.LimitScope;
import com.bone.ailedger.domain.model.LimitVerdict;
import com.bone.aiorch.application.HitlApplicationService;
import com.bone.aiorch.application.OrchestrationApplicationService;
import com.bone.aiorch.application.OrchestrationRequest;
import com.bone.aiorch.application.OrchestrationResult;
import com.bone.aiorch.domain.hitl.HitlItem;
import com.bone.aiorch.domain.hitl.HitlItem.Disposition;
import com.bone.aiorch.domain.hitl.ReasonType;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 智能理赔场景编排器（参考宿主演示：产品侧如何组合 bone-ai 模块拼一个理赔闭环）。
 *
 * <p>场景：医疗费用理赔自动审核。全链路覆盖——①{@code document} 解析理赔条款 PDF → IR（条款切片 + span）；②{@code orchestration}
 * 规则预检（免责/风控）短路 | LLM 评估；③低置信度 / 高风险 → {@code hitl} 人工终审（五态 + ReasonType 回流）。
 *
 * <p>这是"平台只做证明与治理，不做判断与决策"的演示：平台输出 {@link ClaimDecision}（含证据 / 短路信号 / HITL 待决），业务 状态机据其结果自行流转。所有 AI
 * 副作用（调 LLM / 落证据 / 计量）由 gateway / trace 结构强制。
 */
public class ClaimService {

  private static final Logger log = LoggerFactory.getLogger(ClaimService.class);

  /** 置信度强制人工阈值：低于此值必须进 HITL（详设 §4 阈值基线）。 */
  public static final double HITL_FORCE_THRESHOLD = 0.7;

  /** 置信度自动通过阈值：≥此值且非高风险可直接通过（三档分档上档）。 */
  public static final double AUTO_THRESHOLD = 0.85;

  /** 风险等级：高风险进 HITL 强制人工复核。 */
  private static final String HIGH_RISK = "HIGH";

  /** 额度维度：单笔理赔额度（按租户聚合）。 */
  private static final String LIMIT_DIMENSION = "CLAIM_SINGLE";

  /**
   * 单笔理赔额度上限（演示策略：50000 元 / 租户）。
   *
   * <p>只在目标范围<b>尚未登记上限</b>时登记——产品域已登记的额度不被演示默认值覆盖（额度是产品的钱，不该被宿主改写）。
   */
  private static final BigDecimal SINGLE_CLAIM_LIMIT = new BigDecimal("50000");

  private final OrchestrationApplicationService orchestration;
  private final DocumentParser documentParser;
  private final HitlApplicationService hitlService;

  /** 额度内核（可插拔：{@code null} = 未接入控额，行为与接入前一致）。 */
  private final LimitLedger limitLedger;

  public ClaimService(
      OrchestrationApplicationService orchestration,
      DocumentParser documentParser,
      HitlApplicationService hitlService) {
    this(orchestration, documentParser, hitlService, null);
  }

  /**
   * 接入确定性额度内核（两阶段占位：预留 → 提交 / 释放）。
   *
   * <p>2026-10-21 补齐：{@code ClaimServiceConfiguration} 已按需注入 {@link LimitLedger}，但本类仍是 3
   * 参构造（少一个依赖）⇒ 宿主编译不过。 控额语义：<b>超限绝不自动放行</b>——置信度再高也降级转人工，原因码 {@code LIMIT_EXCEEDED}；自动通过则占位即实扣；
   * 转人工则占位待裁（人工 confirm 提交 / reject 释放 / modify 按改后值重占 / escalate 保持占住）。
   */
  public ClaimService(
      OrchestrationApplicationService orchestration,
      DocumentParser documentParser,
      HitlApplicationService hitlService,
      LimitLedger limitLedger) {
    this.orchestration = orchestration;
    this.documentParser = documentParser;
    this.hitlService = hitlService;
    this.limitLedger = limitLedger;
  }

  /** 理赔输入。 */
  public record ClaimRequest(
      String tenantId,
      String claimId,
      String patientDesc,
      double amount,
      String riskLevel,
      InputStream policyPdf,
      String sourceRef) {}

  /** 理赔决策结果（平台输出，业务状态机据此流转）。 */
  public record ClaimDecision(
      String claimId,
      String mode, // SHORT_CIRCUITED / LLM_EVALUATED / HITL_PENDING
      String verdict, // AUTO_APPROVE / AUTO_REJECT / UNDER_REVIEW / ESCALATED
      DocumentIR ir,
      OrchestrationResult orchestration,
      String hitlItemId,
      double confidence,
      String hitlStatus,
      String traceId,
      /**
       * 决策原因码（机器可读）：{@code AUTO_APPROVED} / {@code RULE_SHORT_CIRCUITED} / {@code CONFIDENCE_LOW} /
       * {@code RISK_HIGH} / {@code LIMIT_EXCEEDED}。
       *
       * <p>不靠文案聚合决策原因：{@code HITL_PENDING} 既可能是置信度不足也可能是超限，两者后续路由完全不同——故以稳定码承载（前端 i18n 键 / 监控口径同源）。
       */
      String reasonCode) {

    /** 兼容旧构造（9 参）：原因码按 mode + verdict 推导能确定的两种，其余置空（须由调用方显式给出精确原因码）。 */
    public ClaimDecision(
        String claimId,
        String mode,
        String verdict,
        DocumentIR ir,
        OrchestrationResult orchestration,
        String hitlItemId,
        double confidence,
        String hitlStatus,
        String traceId) {
      this(
          claimId,
          mode,
          verdict,
          ir,
          orchestration,
          hitlItemId,
          confidence,
          hitlStatus,
          traceId,
          deriveReasonCode(mode, verdict));
    }

    private static String deriveReasonCode(String mode, String verdict) {
      if ("SHORT_CIRCUITED".equals(mode)) {
        return ClaimReason.RULE_EXCLUDED.name();
      }
      if ("LLM_EVALUATED".equals(mode) && "AUTO_APPROVE".equals(verdict)) {
        return ClaimReason.AUTO_APPROVED.name();
      }
      return null; // HITL_PENDING 的精确成因（置信度 / 风险 / 超限）只有决策方才知道
    }
  }

  /** 执行理赔审核闭环。 */
  public ClaimDecision underwrite(ClaimRequest request) {
    // 全链 traceId：一次核赔动作共用（规则灰度分桶 → LLM 证据快照 → HITL 关联回放）
    String traceId = "claim-" + request.claimId() + "-" + UUID.randomUUID();

    // ① document：解析理赔条款 → 最小 IR（条款切片 + span）
    ParsedDocument parsed = documentParser.parse(request.policyPdf(), request.sourceRef());
    List<DocumentClause> clauses = ClauseSplitter.split(parsed);
    DocumentIR ir =
        DocumentIR.of(
            request.claimId(),
            request.tenantId(),
            request.sourceRef(),
            parsed.pageCount(),
            "PDFBOX_TEXT",
            parsed.provider(),
            clauses);

    // ② orchestration：规则预检短路 | LLM 评估
    LlmRequest llmRequest =
        LlmRequest.of("claim-assessor", List.of(LlmMessage.user(request.patientDesc())), traceId);
    OrchestrationRequest orchReq =
        new OrchestrationRequest(
            request.tenantId(),
            List.of(),
            Map.of("amount", request.amount()),
            llmRequest,
            true,
            traceId);
    OrchestrationResult orch = orchestration.run(orchReq);

    if (orch.shortCircuited()) {
      log.info(
          "Claim short-circuited: claimId={}, traceId={}, blocked={}",
          request.claimId(),
          traceId,
          orch.blockedRuleCode());
      return new ClaimDecision(
          request.claimId(),
          "SHORT_CIRCUITED",
          "AUTO_REJECT",
          ir,
          orch,
          null,
          1.0,
          null,
          traceId,
          ClaimReason.RULE_EXCLUDED.name());
    }

    // LLM 评估出置信度（从响应解析）
    double confidence = orch.llmResponse() == null ? 0.0 : extractConfidence(orch.llmResponse());

    // ③ 确定性原因分档（AI 可用性 → 风险等级 → 置信度区间）：同一案件可能同时命中多个原因，判定顺序必须固定
    ClaimReason reason = classifyHitlReason(orch.llmResponse(), confidence, request.riskLevel());
    boolean autoPass = reason == ClaimReason.AUTO_APPROVED;
    boolean needHitl = !autoPass;
    String reasonCode = reason.name();

    // ④ 额度内核（可选接入）：先占位再决策——超限绝不自动放行（确定性超限优先于置信度分档）
    String scene = "claim-" + request.claimId();
    String amountText = String.valueOf(request.amount());
    if (limitLedger != null) {
      LimitScope scope = limitScopeOf(request.tenantId());
      ensureLimitDeclared(scope);
      LimitVerdict reservation =
          limitLedger.reserve(reservationIdOf(scene, amountText), scope, amountOf(amountText));
      if (!reservation.ok()) {
        needHitl = true;
        reasonCode = ClaimReason.LIMIT_EXCEEDED.name();
        log.warn(
            "Claim limit exceeded, demoted to HITL: claimId={}, traceId={}, reason={}, detail={}",
            request.claimId(),
            traceId,
            reservation.reason(),
            reservation.detail());
      }
    }

    if (needHitl) {
      HitlItem item =
          hitlService.create(
              request.tenantId(),
              scene,
              "建议核赔",
              amountText,
              request.riskLevel(),
              confidence,
              traceId);
      log.info(
          "Claim escalated to HITL: claimId={}, hitlItemId={}, traceId={}, confidence={}, reason={}",
          request.claimId(),
          item.getId(),
          traceId,
          confidence,
          reasonCode);
      // 额度占位保持待裁（人工 confirm 提交 / reject 释放 / modify 重占）——不因进人工而释放，防并发双花
      return new ClaimDecision(
          request.claimId(),
          "HITL_PENDING",
          "UNDER_REVIEW",
          ir,
          orch,
          item.getId(),
          confidence,
          item.getStatus().name(),
          traceId,
          reasonCode);
    }

    // 自动通过 ⇒ 占位即实扣（无需人工再收尾）
    if (limitLedger != null) {
      limitLedger.commit(reservationIdOf(scene, amountText));
    }
    return new ClaimDecision(
        request.claimId(),
        "LLM_EVALUATED",
        "AUTO_APPROVE",
        ir,
        orch,
        null,
        confidence,
        null,
        traceId,
        ClaimReason.AUTO_APPROVED.name());
  }

  // ---- 额度内核（可选接入）辅助 ----

  /** 额度范围：单笔理赔额度按租户聚合。 */
  private static LimitScope limitScopeOf(String tenantId) {
    return LimitScope.of(LIMIT_DIMENSION, tenantId);
  }

  /**
   * 预留标识：由「场景 + 金额」唯一确定。
   *
   * <p>纯函数化（不依赖 HITL 项 id）有两个好处：①{@code underwrite} 与 {@code act}（只拿到待裁决项）能算出同一个标识；
   * ②同一案件的重复受理天然幂等（台账对同标识同参数返回既有预留）。
   */
  private static String reservationIdOf(String scene, String amountText) {
    return scene + "@" + amountText;
  }

  /** 金额文本 → BigDecimal（与预留标识里的文本严格同源，避免 30000 / 30000.0 两种写法指向两笔预留）。 */
  private static BigDecimal amountOf(String amountText) {
    return new BigDecimal(amountText);
  }

  /** 登记上限（仅在目标范围尚未登记时登记——产品域已登记的额度不被演示默认值覆盖）。 */
  private void ensureLimitDeclared(LimitScope scope) {
    if (limitLedger.snapshot(scope).limit().signum() <= 0) {
      limitLedger.declareLimit(scope, SINGLE_CLAIM_LIMIT);
    }
  }

  /**
   * 人工确认：提交占位（预留 → 已实际占用）。
   *
   * <p>若占位不存在（当初因超限未能预留，人工事后核准放行），先补占位再提交——补占位失败则记录告警：人的裁决是既成事实，额度台账的审计留痕记录
   * 这次失败（碰钱的每一次变动都留痕），但不因记账失败推翻人工决策。
   */
  private void commitReservation(String tenantId, String reservationId, String amountText) {
    LimitVerdict verdict = limitLedger.commit(reservationId);
    if (verdict.ok()) {
      return;
    }
    LimitScope scope = limitScopeOf(tenantId);
    LimitVerdict reserved = limitLedger.reserve(reservationId, scope, amountOf(amountText));
    if (reserved.ok()) {
      limitLedger.commit(reservationId);
      return;
    }
    log.warn(
        "Claim limit commit failed (human decision kept, ledger audited): reservationId={}, reason={}, detail={}",
        reservationId,
        reserved.reason(),
        reserved.detail());
  }

  /** 人工否决 / 改额：释放占位（额度返还可用）。 */
  private void releaseReservation(String reservationId) {
    LimitVerdict verdict = limitLedger.release(reservationId);
    if (!verdict.ok()) {
      log.warn(
          "Claim limit release skipped: reservationId={}, reason={}, detail={}",
          reservationId,
          verdict.reason(),
          verdict.detail());
    }
  }

  /** 人工改额：按改后值重新占位并实扣（先释放原占位，见调用方）。 */
  private void reserveAndCommit(String tenantId, String reservationId, String amountText) {
    LimitScope scope = limitScopeOf(tenantId);
    LimitVerdict reserved = limitLedger.reserve(reservationId, scope, amountOf(amountText));
    if (!reserved.ok()) {
      log.warn(
          "Claim limit re-reserve failed: reservationId={}, reason={}, detail={}",
          reservationId,
          reserved.reason(),
          reserved.detail());
      return;
    }
    limitLedger.commit(reservationId);
  }

  /**
   * 置信度从 LLM 响应文本提取。
   *
   * <p>支持三种产出形态（模型自评格式不统一，解析须容错）：①结构化 JSON 字段 {@code "confidence":0.9}（含嵌套，如 {@code
   * {"result":{"confidence":0.42}}}）； ②半结构化文本 {@code conf:0.85}；③都没有 → 缺省值。
   *
   * <p><b>缺省值语义（2026-10-17 复核修复）</b>：未携带置信度时返回 <b>0.0</b>（强制人工），而非此前的 0.9—— 模型没给置信度就「自动核赔通过」是
   * fail-open，与合规场景「拿不出证据就转人工」的基本原则相反。
   */
  static double extractConfidence(LlmResponse response) {
    String content = response.content();
    if (content == null) {
      return 0.0;
    }
    // ①结构化 JSON（含嵌套：只认带引号的字段名，避免与散文里的 confidence 混淆）
    java.util.regex.Matcher jsonField =
        java.util.regex.Pattern.compile("\"confidence\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
            .matcher(content);
    if (jsonField.find()) {
      try {
        return Double.parseDouble(jsonField.group(1));
      } catch (NumberFormatException ignored) {
        // 非数字则继续回落
      }
    }
    // ②半结构化文本
    int idx = content.indexOf("conf:");
    if (idx >= 0) {
      try {
        return Double.parseDouble(content.substring(idx + 5).trim().split("[^0-9.]")[0]);
      } catch (NumberFormatException ignored) {
        // 非数字则回落
      }
    }
    return 0.0;
  }

  /**
   * 确定性原因分档（同一案件可能同时命中多个原因，判定顺序必须固定）。
   *
   * <p>优先级：<b>AI 可用性 → 风险等级 → 置信度区间</b>。①连模型响应都没有时谈置信度没意义（且绝不能因此自动放行）；②再高的置信度也不越过风控红线； ③最后才按置信度分档。
   *
   * @param response 模型响应（{@code null} = AI 不可用）
   * @param confidence 已解析的置信度（0-1）
   * @param riskLevel 风险等级（{@code HIGH} / {@code MEDIUM} / {@code LOW}）
   */
  static ClaimReason classifyHitlReason(LlmResponse response, double confidence, String riskLevel) {
    if (response == null) {
      return ClaimReason.AI_UNAVAILABLE;
    }
    if (HIGH_RISK.equals(riskLevel)) {
      return ClaimReason.HIGH_RISK;
    }
    if (confidence < HITL_FORCE_THRESHOLD) {
      return ClaimReason.CONFIDENCE_LOW;
    }
    if (confidence < AUTO_THRESHOLD) {
      return ClaimReason.CONFIDENCE_REVIEW;
    }
    return ClaimReason.AUTO_APPROVED;
  }

  /**
   * 人工终审入口（业务状态机裁决后回写）：confirm / reject / modify / escalate，返回该次处置记录（含 ReasonType 回流）。
   *
   * <p><b>额度收尾（可选接入）</b>：{@code confirm} 提交占位（预留转实扣）、{@code reject} 释放占位（额度返还）、{@code modify}
   * 先释放原占位再按改后值重占并 实扣、{@code escalate} 保持占住（仍在审 ⇒ 额度继续占住，防后续案件抢占用）。额度动作在人工裁决<b>之后</b>执行：裁决失败不应先动钱。
   */
  public Disposition act(
      String tenantId,
      String itemId,
      String actor,
      String action,
      Object value,
      ReasonType reasonType,
      String comment) {
    // 额度收尾所需的上下文（场景 + 建议金额）——裁决前先取，避免 modify 后建议值已被改写
    HitlItem pending =
        limitLedger == null ? null : hitlService.findPendingById(tenantId, itemId).orElse(null);
    String pendingScene = pending == null ? null : pending.getScene();
    String pendingAmount = pending == null ? null : pending.getSuggestedValue();
    String pendingReservation =
        pendingScene == null ? null : reservationIdOf(pendingScene, pendingAmount);

    HitlItem item;
    switch (action) {
      case "confirm":
        item = hitlService.confirm(tenantId, itemId, actor, reasonType);
        if (limitLedger != null && pendingReservation != null) {
          commitReservation(tenantId, pendingReservation, pendingAmount);
        }
        break;
      case "reject":
        item = hitlService.reject(tenantId, itemId, actor, reasonType, comment);
        if (limitLedger != null && pendingReservation != null) {
          releaseReservation(pendingReservation);
        }
        break;
      case "modify":
        String modifiedValue = value == null ? null : String.valueOf(value);
        item = hitlService.modify(tenantId, itemId, actor, modifiedValue, reasonType, comment);
        if (limitLedger != null && pendingReservation != null && modifiedValue != null) {
          // 先释放原占位，再按改后值重新占位并实扣（改后值超限则不占用，告警留痕）
          releaseReservation(pendingReservation);
          reserveAndCommit(tenantId, reservationIdOf(pendingScene, modifiedValue), modifiedValue);
        }
        break;
      case "escalate":
        item = hitlService.escalate(tenantId, itemId, actor, comment);
        // 占位保持：仍在审 ⇒ 额度继续占住（不释放）
        break;
      default:
        throw new IllegalArgumentException("unknown action: " + action);
    }
    List<Disposition> chain = item.dispositionHistory();
    return chain.get(chain.size() - 1);
  }

  /** 处置链回放（谁在何时做了什么裁决、带什么原因——可回放证据，读持久化）。 */
  public List<Disposition> dispositionHistory(String tenantId, String itemId) {
    return hitlService.dispositionHistory(tenantId, itemId);
  }
}
