package com.bone.procurement.contract.domain.model.review;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 审查聚合根：状态机 + 人工终决留痕。
 *
 * <p>合法迁移：PENDING→(PARSED|FAILED)、PARSED→(IN_REVIEW|HUMAN_REVIEW)、IN_REVIEW→HUMAN_REVIEW、
 * FAILED→HUMAN_REVIEW、HUMAN_REVIEW→SNAPSHOTTED。 SNAPSHOTTED 为终态，任何再迁移一律拒绝。
 *
 * <p><b>人工闸门不可绕过</b>：IN_REVIEW 只能到 HUMAN_REVIEW，快照终态必须经过人工终决（不保留 IN_REVIEW→SNAPSHOTTED 后门）。submit()
 * 路径总是 routeToHuman，因此不受影响。
 */
public final class ContractReview {

  private static final Map<ReviewStatus, Set<ReviewStatus>> ALLOWED =
      new EnumMap<>(ReviewStatus.class);

  static {
    ALLOWED.put(ReviewStatus.PENDING, Set.of(ReviewStatus.PARSED, ReviewStatus.FAILED));
    ALLOWED.put(ReviewStatus.PARSED, Set.of(ReviewStatus.IN_REVIEW, ReviewStatus.HUMAN_REVIEW));
    ALLOWED.put(ReviewStatus.IN_REVIEW, Set.of(ReviewStatus.HUMAN_REVIEW));
    ALLOWED.put(ReviewStatus.HUMAN_REVIEW, Set.of(ReviewStatus.SNAPSHOTTED));
    ALLOWED.put(ReviewStatus.FAILED, Set.of(ReviewStatus.HUMAN_REVIEW));
    ALLOWED.put(ReviewStatus.SNAPSHOTTED, Set.of());
  }

  private final String reviewId;
  private final String contractNo;
  private final List<HumanOverride> overrides = new ArrayList<>();

  private ReviewStatus status;
  private String failureReason;
  private String humanReason;
  private AiParticipation aiParticipation = AiParticipation.UNKNOWN;
  private RiskLevel riskLevel = RiskLevel.UNKNOWN;
  private List<RiskItem> riskItems = List.of();
  private ReviewSnapshotRef reviewSnapshotRef;
  private String modelVersionSnapshot;
  private double aiConfidence;
  private boolean humanFinalized;

  private ContractReview(String reviewId, String contractNo) {
    this.reviewId = Objects.requireNonNull(reviewId, "reviewId");
    this.contractNo = Objects.requireNonNull(contractNo, "contractNo");
    this.status = ReviewStatus.PENDING;
  }

  public static ContractReview pending(String reviewId, String contractNo) {
    return new ContractReview(reviewId, contractNo);
  }

  /**
   * 持久化重建工厂：仅供 infrastructure 适配器从已落库快照恢复聚合，刻意绕过状态机迁移校验。
   *
   * <p>已落库数据本身经过状态机写入，重建时再走一遍迁移会丢失 overrides / snapshot 等不可变历史； 因此这里直接还原字段，禁止业务代码调用。
   */
  public static ContractReview rehydrate(
      String reviewId,
      String contractNo,
      ReviewStatus status,
      String failureReason,
      String humanReason,
      AiParticipation aiParticipation,
      RiskLevel riskLevel,
      List<RiskItem> riskItems,
      ReviewSnapshotRef snapshotRef,
      String modelVersionSnapshot,
      double aiConfidence,
      List<HumanOverride> overrides,
      boolean humanFinalized) {
    ContractReview review = new ContractReview(reviewId, contractNo);
    review.status = Objects.requireNonNull(status, "status");
    review.failureReason = failureReason;
    review.humanReason = humanReason;
    review.aiParticipation = aiParticipation == null ? AiParticipation.UNKNOWN : aiParticipation;
    review.riskLevel = riskLevel == null ? RiskLevel.UNKNOWN : riskLevel;
    review.riskItems = riskItems == null ? List.of() : List.copyOf(riskItems);
    review.reviewSnapshotRef = snapshotRef;
    review.modelVersionSnapshot = modelVersionSnapshot;
    review.aiConfidence = aiConfidence;
    review.overrides.addAll(overrides == null ? List.of() : overrides);
    review.humanFinalized = humanFinalized;
    return review;
  }

  public void markParsed() {
    transitionTo(ReviewStatus.PARSED);
  }

  public void markParseFailed(String reason) {
    transitionTo(ReviewStatus.FAILED);
    this.failureReason = reason;
  }

  public void startReview() {
    transitionTo(ReviewStatus.IN_REVIEW);
  }

  public void routeToHuman(String reason) {
    transitionTo(ReviewStatus.HUMAN_REVIEW);
    this.humanReason = reason;
  }

  public void snapshot(ReviewSnapshotRef ref) {
    // 两层防护分工：状态机先裁决「迁移合法性」（IN_REVIEW/PARSED→SNAPSHOTTED 仍抛含状态名的原错误，
    // 保持原有契约）；仅当迁移本身合法（HUMAN_REVIEW→SNAPSHOTTED）时，再校验人工终决留痕。
    // 缺此断言则 routeToHuman 后可绕过 recordHumanOverride 直接快照。
    if (status == ReviewStatus.HUMAN_REVIEW && !humanFinalized) {
      throw new IllegalStateException("必须先完成人工终决留痕才能快照");
    }
    transitionTo(ReviewStatus.SNAPSHOTTED);
    this.reviewSnapshotRef = Objects.requireNonNull(ref, "reviewSnapshotRef");
  }

  /** 采纳 AI 建议仅作为待复核输入，不替代人工终决。 */
  public void applyAiSuggestion(ReviewSuggestion suggestion) {
    if (status != ReviewStatus.IN_REVIEW) {
      throw new IllegalStateException("只能在 IN_REVIEW 采纳 AI 建议，当前=" + status);
    }
    Objects.requireNonNull(suggestion, "suggestion");
    this.aiParticipation = suggestion.aiAvailable();
    this.riskLevel = suggestion.riskLevel();
    this.riskItems = suggestion.riskItems();
    this.modelVersionSnapshot = suggestion.modelVersionSnapshot();
    this.aiConfidence = suggestion.confidence();
  }

  /** 人工推翻：留痕并改写终决风险等级；AI 结论不被静默采纳。 */
  public void recordHumanOverride(HumanOverride override) {
    if (status != ReviewStatus.HUMAN_REVIEW) {
      throw new IllegalStateException("只能在 HUMAN_REVIEW 记录人工推翻，当前=" + status);
    }
    Objects.requireNonNull(override, "override");
    this.overrides.add(override);
    this.humanReason = override.reason();
    this.riskLevel = override.finalRiskLevel();
    this.humanFinalized = true;
  }

  private void transitionTo(ReviewStatus target) {
    Set<ReviewStatus> allowed = ALLOWED.getOrDefault(status, Set.of());
    if (!allowed.contains(target)) {
      throw new IllegalStateException("非法状态迁移: " + status + " -> " + target);
    }
    this.status = target;
  }

  public String reviewId() {
    return reviewId;
  }

  public String contractNo() {
    return contractNo;
  }

  public ReviewStatus status() {
    return status;
  }

  public String failureReason() {
    return failureReason;
  }

  public String humanReason() {
    return humanReason;
  }

  public AiParticipation aiParticipation() {
    return aiParticipation;
  }

  public RiskLevel riskLevel() {
    return riskLevel;
  }

  public List<RiskItem> riskItems() {
    return riskItems;
  }

  public ReviewSnapshotRef reviewSnapshotRef() {
    return reviewSnapshotRef;
  }

  public String modelVersionSnapshot() {
    return modelVersionSnapshot;
  }

  public double aiConfidence() {
    return aiConfidence;
  }

  public List<HumanOverride> overrides() {
    return List.copyOf(overrides);
  }

  public boolean isHumanFinalized() {
    return humanFinalized;
  }
}
