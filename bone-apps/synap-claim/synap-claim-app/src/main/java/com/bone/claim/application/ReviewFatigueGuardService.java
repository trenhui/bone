package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.config.ReviewFatigueConfig;
import com.bone.claim.domain.model.review.ReviewFatigueDecision;
import com.bone.claim.domain.repository.ClaimTaskRepository;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审核疲劳评估（只读；工作台 soft prompt 的数据源）。
 *
 * <p>连续时长与距班末分钟由调用方传入——服务端没有可信的班次事实源，<b>不伪造班次</b>。 金额只用于「敏感案件是否适合派给该复核人」的提示，不进入理算 / 拒赔 / 支付。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewFatigueGuardService {

  private final FeatureTenantToggleService featureTenantToggleService;
  private final ClaimTaskRepository claimTaskRepository;
  private final TenantPort tenantPort;

  /** 只读评估；任何取数异常都不外溢，且按不利方向（超量）收敛。 */
  @Transactional(readOnly = true)
  public ReviewFatigueDecision evaluate(
      String assigneeId,
      Integer continuousMinutes,
      Integer minutesToShiftEnd,
      BigDecimal claimAmount) {
    long tenantId = tenantPort.currentTenantId();
    ReviewFatigueConfig config = featureTenantToggleService.reviewFatigueConfig(tenantId);
    if (config == null) {
      config = ReviewFatigueConfig.defaults();
    }
    boolean overloaded = false;
    if (assigneeId != null && !assigneeId.isBlank()) {
      try {
        long finished =
            claimTaskRepository.countFinishedTodayByAssignee(
                tenantId, assigneeId, ClaimTaskApplicationService.utcDayStart(Instant.now()));
        overloaded = finished >= config.maxPerShift();
      } catch (RuntimeException e) {
        overloaded = true;
        log.warn(
            "[FATIGUE_EVAL] 完成量取数失败，评估按超量收敛：tenantId={}, assigneeId={}, err={}",
            tenantId,
            assigneeId,
            e.getMessage());
      }
    }
    boolean restPrompt =
        continuousMinutes != null && continuousMinutes >= config.restPromptMinutes();
    boolean sensitiveEligible =
        !(config.sensitiveAmountReached(claimAmount)
            && minutesToShiftEnd != null
            && minutesToShiftEnd < config.shiftEndExclusionMinutes());
    return new ReviewFatigueDecision(overloaded, restPrompt, sensitiveEligible, config);
  }
}
