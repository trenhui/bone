package com.bone.procurement.contract.application;

import com.bone.procurement.contract.domain.gateway.ContractParsePort;
import com.bone.procurement.contract.domain.gateway.ContractReviewIdentityPort;
import com.bone.procurement.contract.domain.gateway.ContractReviewPort;
import com.bone.procurement.contract.domain.gateway.ContractReviewStore;
import com.bone.procurement.contract.domain.gateway.ContractReviewTenantPort;
import com.bone.procurement.contract.domain.gateway.ReviewTracePort;
import com.bone.procurement.contract.domain.model.review.ContractParseFailedException;
import com.bone.procurement.contract.domain.model.review.ContractReview;
import com.bone.procurement.contract.domain.model.review.ContractReviewedEvent;
import com.bone.procurement.contract.domain.model.review.CrossTenantAccessException;
import com.bone.procurement.contract.domain.model.review.HumanOverride;
import com.bone.procurement.contract.domain.model.review.ParsedClause;
import com.bone.procurement.contract.domain.model.review.ParsedContract;
import com.bone.procurement.contract.domain.model.review.ReviewNotFoundException;
import com.bone.procurement.contract.domain.model.review.ReviewRequest;
import com.bone.procurement.contract.domain.model.review.ReviewSnapshotRef;
import com.bone.procurement.contract.domain.model.review.ReviewStatus;
import com.bone.procurement.contract.domain.model.review.ReviewSuggestion;
import com.bone.procurement.contract.domain.model.review.ReviewTraceEntry;
import com.bone.procurement.contract.domain.service.ReviewEventAssembler;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 合同审查应用服务：提交审查 / 查询报告 / 人工推翻 / 披露查询四用例。
 *
 * <p>红线：租户从登录态端口取、不接自报，租户不一致 fail-closed；AI 结论仅作待复核建议，任何路径都不自动终决；降级 / 停用 / 推翻入追溯链。 能力开关关闭时本 bean
 * 不注册。
 */
@Service
@ConditionalOnContractAiReviewEnabled
public class ContractReviewApplicationService {

  private final ContractParsePort parsePort;
  private final ContractReviewPort reviewPort;
  private final ReviewTracePort tracePort;
  private final ContractReviewStore store;
  private final ContractReviewTenantPort tenantPort;
  private final ContractReviewIdentityPort identityPort;
  private final AiDisclosureService disclosureService;
  private final ContractReviewedEventPublisher eventPublisher;
  private final HitlRoutingPolicy hitlPolicy = new HitlRoutingPolicy();

  public ContractReviewApplicationService(
      ContractParsePort parsePort,
      ContractReviewPort reviewPort,
      ReviewTracePort tracePort,
      ContractReviewStore store,
      ContractReviewTenantPort tenantPort,
      ContractReviewIdentityPort identityPort,
      AiDisclosureService disclosureService,
      ContractReviewedEventPublisher eventPublisher) {
    this.parsePort = Objects.requireNonNull(parsePort, "parsePort");
    this.reviewPort = Objects.requireNonNull(reviewPort, "reviewPort");
    this.tracePort = Objects.requireNonNull(tracePort, "tracePort");
    this.store = Objects.requireNonNull(store, "store");
    this.tenantPort = Objects.requireNonNull(tenantPort, "tenantPort");
    this.identityPort = Objects.requireNonNull(identityPort, "identityPort");
    this.disclosureService = Objects.requireNonNull(disclosureService, "disclosureService");
    this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
  }

  /** 用例一：提交审查。解析 → AI 建议（仅建议）→ 确定性 HITL → 停在人工复核，不自动终决。 */
  public ReviewReport submit(SubmitReviewCommand command) {
    String tenantId = requireTenant();
    // reviewId 必须服务端生成：请求体不接受业务键，避免跨租户覆写与自报绕过。
    String reviewId = "rv-" + UUID.randomUUID();
    ContractReview review = ContractReview.pending(reviewId, command.contractNo());
    try {
      ParsedContract ir =
          parsePort.parse(command.document(), tenantId, command.sourceRef(), command.docId());
      review.markParsed();
      ReviewRequest request =
          new ReviewRequest(reviewId, command.contractNo(), tenantId, clausesOf(ir));
      ReviewSuggestion suggestion = reviewPort.review(request);
      review.startReview();
      review.applyAiSuggestion(suggestion);
      HitlRoutingDecision decision =
          hitlPolicy.route(
              suggestion.confidence(), suggestion.riskLevel(), suggestion.negativeHit());
      review.routeToHuman(humanReason(decision));
      if (suggestion.aiAvailable().isCannotConfirm()) {
        tracePort.append(
            ReviewTraceEntry.of(
                review.reviewId(), "AI_DEGRADED", "aiAvailable=" + suggestion.aiAvailable()));
      }
    } catch (ContractParseFailedException e) {
      if (review.status() == ReviewStatus.PENDING) {
        review.markParseFailed(e.getMessage());
      }
      review.routeToHuman("合同解析失败转人工: " + e.getMessage());
      tracePort.append(ReviewTraceEntry.of(review.reviewId(), "PARSE_FAILED", e.getMessage()));
    } catch (RuntimeException e) {
      if (review.status() == ReviewStatus.PENDING) {
        review.markParseFailed(e.getMessage());
      }
      if (review.status() == ReviewStatus.PARSED || review.status() == ReviewStatus.IN_REVIEW) {
        review.routeToHuman("AI 审查异常转人工: " + e.getMessage());
      }
      tracePort.append(ReviewTraceEntry.of(review.reviewId(), "AI_ERROR", e.getMessage()));
    }
    // 幂等投递：同租户同幂等键只落一条，重复投递返回原 review_id 与原审查快照。
    String effectiveReviewId =
        store.saveIdempotent(tenantId, idempotencyKey(command, reviewId), review);
    if (!effectiveReviewId.equals(review.reviewId())) {
      return store
          .find(tenantId, effectiveReviewId)
          .map(ContractReviewApplicationService::toReport)
          .orElseGet(() -> toReport(review));
    }
    return toReport(review);
  }

  /** 幂等键优先取请求携带值，其次文档引用 / 来源引用，最后退回服务端 reviewId；租户不参与自报。 */
  private static String idempotencyKey(SubmitReviewCommand command, String reviewId) {
    if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
      String key = command.idempotencyKey().trim();
      return key.length() > 128 ? key.substring(0, 128) : key;
    }
    if (command.docId() != null && !command.docId().isBlank()) {
      return truncate("doc:" + command.docId());
    }
    if (command.sourceRef() != null && !command.sourceRef().isBlank()) {
      return truncate("src:" + command.sourceRef());
    }
    return "review:" + reviewId;
  }

  private static String truncate(String value) {
    return value.length() > 128 ? value.substring(0, 128) : value;
  }

  /** 用例二：查询报告。 */
  public ReviewReport getReport(String reviewId) {
    return toReport(requireOwned(reviewId));
  }

  /** 用例三：人工推翻；记录 AI 结论与人工终决、留痕，并进入快照终态。 */
  public ReviewReport override(String reviewId, HumanOverrideInput input) {
    ContractReview review = requireOwned(reviewId);
    String reviewer = identityPort.currentReviewer();
    if (reviewer == null || reviewer.isBlank()) {
      throw new IllegalStateException("人工终决需要登录态身份，禁止匿名/自报复核");
    }
    if (input.reviewer() != null
        && !input.reviewer().isBlank()
        && !input.reviewer().trim().equals(reviewer)) {
      throw new IllegalArgumentException("请求复核人与登录态不一致，禁止冒名复核");
    }
    if (input.reason() == null || input.reason().isBlank()) {
      throw new IllegalArgumentException("人工终决理由不得为空");
    }
    if (input.finalRiskLevel() == null) {
      throw new IllegalArgumentException("人工终决风险等级不得为空");
    }
    review.recordHumanOverride(
        new HumanOverride(
            reviewer,
            input.reason(),
            review.aiConfidence(),
            review.riskLevel(),
            input.finalRiskLevel()));
    tracePort.append(
        ReviewTraceEntry.of(
            reviewId,
            "HUMAN_OVERRIDE",
            "reviewer=" + reviewer + ", final=" + input.finalRiskLevel()));
    review.snapshot(ReviewSnapshotRef.of("snap://override/" + reviewId));

    // 审查结论落库与 Outbox 插入同一事务；消费由显式驱动扫描 Outbox 完成（本地模拟投递）。
    // 生产 MQ producer/listener 仍属 L3，不得把显式驱动表述为已交付的生产 MQ 行为。
    ContractReviewedEvent event = ReviewEventAssembler.assemble(review);
    eventPublisher.publishWithConclusion(requireTenant(), review, event);
    return toReport(review);
  }

  /** 用例四：披露查询；未知态原样返回，版本快照不作参与证据。 */
  public AiDisclosureView disclosure(String reviewId) {
    return disclosureService.disclose(requireOwned(reviewId));
  }

  private ContractReview requireOwned(String reviewId) {
    String tenantId = requireTenant();
    return store
        .find(tenantId, reviewId)
        .orElseThrow(
            () ->
                store.existsGlobally(reviewId)
                    ? new CrossTenantAccessException("跨租户访问拒绝: reviewId=" + reviewId)
                    : new ReviewNotFoundException("审查记录不存在: reviewId=" + reviewId));
  }

  private String requireTenant() {
    String tenantId = tenantPort.currentTenantId();
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalStateException("需要登录租户上下文，不接受请求自报租户");
    }
    return tenantId;
  }

  private static List<ParsedClause> clausesOf(ParsedContract ir) {
    return ir == null ? List.of() : ir.clauses();
  }

  private static String humanReason(HitlRoutingDecision decision) {
    return decision.humanReviewRequired() ? "HITL 强制人工: " + decision.triggers() : "AI 建议待人工终决";
  }

  private static ReviewReport toReport(ContractReview review) {
    return new ReviewReport(
        review.reviewId(),
        review.contractNo(),
        review.status().name(),
        review.aiParticipation(),
        review.riskLevel(),
        review.isHumanFinalized(),
        review.humanReason());
  }
}
