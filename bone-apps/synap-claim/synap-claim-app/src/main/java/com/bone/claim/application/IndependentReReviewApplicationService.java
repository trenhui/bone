package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.common.PageBounds;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.config.FeatureToggle;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.core.exception.DomainException;
import com.bone.core.model.PageResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 独立复审应用服务（批 3，任务 3.1 / 3.2 / 4.1）。
 *
 * <p>职责：
 *
 * <ol>
 *   <li><b>写操作收口</b>： submits / completes 都经 RE_REVIEW 开关 fail-closed（任务 4.1）——关闭时不产生任何 43/46 流转，44
 *       双人复核与初审入口完全不受影响；
 *   <li><b>职责分离硬约束</b>：领域层 {@link ClaimCase#submitForIndependentReReview(String)} 已钉「复审人 ≠ 初审人」，本层把
 *       {@link DomainException} 翻译成可辨认业务码 {@code SC_CLAIM_REREVIEWER_CONFLICT}，不依赖 UI 隐藏；
 *   <li><b>复审队列</b>：按 tenantId + 状态 43 + 持权人（{@code secondReviewerId}）过滤，跨租户不可见、只出 43 状态案件（任务 3.1）。
 * </ol>
 *
 * <p>本类不直接调 {@code Repository.save}：案件落库与领域事件发布统一委托 {@link ClaimCaseApplicationService#saveClaim}，
 * 避免重复实现乐观锁重试与 save/publish 配对（ArchUnit E-5.4）。
 */
@Service
@RequiredArgsConstructor
public class IndependentReReviewApplicationService {

  private final ClaimCaseRepository claimCaseRepository;
  private final ClaimCaseApplicationService claimCaseApplicationService;
  private final FeatureTenantToggleService featureTenantToggleService;
  private final TenantPort tenantPort;

  /** 复审队列行（持权人视角；ID 一律 string，前端不做数值运算）。 */
  public record ReReviewQueueRow(
      String claimId,
      String claimNo,
      String status,
      String secondReviewerId,
      String claimType,
      java.time.Instant createdAt) {}

  /** 提交独立复审：42 → 43，记录独立复审人（非初审人）。 */
  @Transactional
  public ReReviewQueueRow submitForReReview(long claimId, String reviewerId) {
    requireFeatureEnabled();
    ClaimCase claim = requireClaim(claimId);
    try {
      claim.submitForIndependentReReview(reviewerId);
    } catch (DomainException ex) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_REREVIEWER_CONFLICT, ex.getMessage());
    }
    claimCaseApplicationService.saveClaim(claim);
    return toRow(claim);
  }

  /** 提交复审结论：43 → 46 通过 / 43 → 45 驳回；复审人必须是登记的独立复审人。 */
  @Transactional
  public ReReviewQueueRow completeReReview(long claimId, boolean approved, String reviewerId) {
    requireFeatureEnabled();
    ClaimCase claim = requireClaim(claimId);
    try {
      claim.completeIndependentReReview(approved, reviewerId);
    } catch (DomainException ex) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_REREVIEWER_CONFLICT, ex.getMessage());
    }
    claimCaseApplicationService.saveClaim(claim);
    return toRow(claim);
  }

  /** 复审队列（分页，只出 43 状态案件，按持权人 + 租户隔离）。 */
  @Transactional(readOnly = true)
  public PageResult<ReReviewQueueRow> queue(String reviewerId, int page, int size) {
    long tenantId = tenantPort.currentTenantId();
    PageBounds bounds = PageBounds.of(page, size);
    List<ClaimCase> rows =
        claimCaseRepository.findReReviewQueue(tenantId, reviewerId, bounds.page(), bounds.size());
    long total = claimCaseRepository.countReReviewQueue(tenantId, reviewerId);
    return PageResult.of(
        rows.stream().map(IndependentReReviewApplicationService::toRow).toList(),
        total,
        bounds.page(),
        bounds.size());
  }

  /** RE_REVIEW 开关 fail-closed：未配置 / 关闭一律不放行 43/46 流转。 */
  private void requireFeatureEnabled() {
    long tenantId = tenantPort.currentTenantId();
    if (!featureTenantToggleService.isFeatureAllowed(
        tenantId, true, FeatureToggle.FEATURE_RE_REVIEW)) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_STATUS_CONFLICT, "RE_REVIEW 开关关闭，独立复审不可用");
    }
  }

  private ClaimCase requireClaim(long claimId) {
    ClaimCase claim = claimCaseRepository.findById(claimId);
    if (claim == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_NOT_FOUND, claimId);
    }
    return claim;
  }

  private static ReReviewQueueRow toRow(ClaimCase c) {
    return new ReReviewQueueRow(
        String.valueOf(c.getId()),
        c.getClaimNo(),
        c.getStatus(),
        c.getSecondReviewerId(),
        c.getClaimType(),
        c.getCreatedAt());
  }
}
