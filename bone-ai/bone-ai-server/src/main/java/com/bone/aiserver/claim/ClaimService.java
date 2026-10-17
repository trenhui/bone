package com.bone.aiserver.claim;

import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.aidoc.domain.gateway.DocumentParser;
import com.bone.aidoc.domain.gateway.ParsedDocument;
import com.bone.aidoc.domain.model.DocumentClause;
import com.bone.aidoc.domain.model.DocumentIR;
import com.bone.aidoc.domain.service.ClauseSplitter;
import com.bone.aiorch.application.HitlApplicationService;
import com.bone.aiorch.application.OrchestrationApplicationService;
import com.bone.aiorch.application.OrchestrationRequest;
import com.bone.aiorch.application.OrchestrationResult;
import com.bone.aiorch.domain.hitl.HitlItem;
import com.bone.aiorch.domain.hitl.HitlItem.Disposition;
import com.bone.aiorch.domain.hitl.ReasonType;
import java.io.InputStream;
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

  private final OrchestrationApplicationService orchestration;
  private final DocumentParser documentParser;
  private final HitlApplicationService hitlService;

  public ClaimService(
      OrchestrationApplicationService orchestration,
      DocumentParser documentParser,
      HitlApplicationService hitlService) {
    this.orchestration = orchestration;
    this.documentParser = documentParser;
    this.hitlService = hitlService;
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
      String traceId) {}

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
          request.claimId(), "SHORT_CIRCUITED", "AUTO_REJECT", ir, orch, null, 1.0, null, traceId);
    }

    // LLM 评估出置信度（从响应解析）
    double confidence = orch.llmResponse() == null ? 0.0 : extractConfidence(orch.llmResponse());

    // ③ 置信度三档分档（详设 §4 初值）：≥0.85 且非高风险直出；0.7-0.85 复核；<0.7 或高风险强制人工终审
    boolean autoPass = confidence >= AUTO_THRESHOLD && !HIGH_RISK.equals(request.riskLevel());
    boolean needHitl = !autoPass;
    if (needHitl) {
      HitlItem item =
          hitlService.create(
              request.tenantId(),
              "claim-" + request.claimId(),
              "建议核赔",
              String.valueOf(request.amount()),
              request.riskLevel(),
              confidence,
              traceId);
      log.info(
          "Claim escalated to HITL: claimId={}, hitlItemId={}, traceId={}, confidence={}",
          request.claimId(),
          item.getId(),
          traceId,
          confidence);
      return new ClaimDecision(
          request.claimId(),
          "HITL_PENDING",
          "UNDER_REVIEW",
          ir,
          orch,
          item.getId(),
          confidence,
          item.getStatus().name(),
          traceId);
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
        traceId);
  }

  /**
   * 置信度从 LLM 响应文本提取：{@code conf:0.85} 形如模型自评。
   *
   * <p><b>缺省值语义（2026-10-17 复核修复）</b>：未携带置信度时返回 <b>0.0</b>（强制人工），而非此前的 0.9—— 模型没给置信度就「自动核赔通过」是
   * fail-open，与合规场景「拿不出证据就转人工」的基本原则相反。
   */
  static double extractConfidence(LlmResponse response) {
    String content = response.content();
    if (content == null) {
      return 0.0;
    }
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

  /** 人工终审入口（业务状态机裁决后回写）：confirm / reject / modify，返回该次处置记录（含 ReasonType 回流）。 */
  public Disposition act(
      String tenantId,
      String itemId,
      String actor,
      String action,
      Object value,
      ReasonType reasonType,
      String comment) {
    HitlItem item;
    switch (action) {
      case "confirm":
        item = hitlService.confirm(tenantId, itemId, actor, reasonType);
        break;
      case "reject":
        item = hitlService.reject(tenantId, itemId, actor, reasonType, comment);
        break;
      case "modify":
        item =
            hitlService.modify(
                tenantId,
                itemId,
                actor,
                value == null ? null : String.valueOf(value),
                reasonType,
                comment);
        break;
      case "escalate":
        item = hitlService.escalate(tenantId, itemId, actor, comment);
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
