package com.bone.claim.domain.service;

import com.bone.claim.domain.model.config.ReviewFatigueConfig;
import com.bone.claim.domain.model.task.AssigneeType;
import com.bone.claim.domain.port.out.AssigneeEligibilityPort;
import java.util.Objects;
import java.util.function.Function;

/**
 * 审核疲劳准入包装器（只做判据，不查库、不写库）。
 *
 * <p>包裹既有 {@link AssigneeEligibilityPort}：形态判定仍由原端口负责，本类只对 {@code USER} 增加
 * 「当日完成量未达上限」判据。计数由调用方以函数注入，异常或不可信结果（null / 负数）一律判候选不可用—— 绝不因为「查不到」而把状态未知的人派进任务。
 *
 * <p>超量不是收单失败：返回 false 后由 {@link TaskAssigner} 既有 ALL_INELIGIBLE 回落入池，任务中心可再派。
 */
public final class FatigueAwareEligibilityPort implements AssigneeEligibilityPort {

  private final AssigneeEligibilityPort delegate;
  private final ReviewFatigueConfig config;
  private final Function<String, Long> finishedToday;

  public FatigueAwareEligibilityPort(
      AssigneeEligibilityPort delegate,
      ReviewFatigueConfig config,
      Function<String, Long> finishedToday) {
    this.delegate = Objects.requireNonNull(delegate, "被包装的准入端口不能为空");
    this.config = Objects.requireNonNull(config, "疲劳配置不能为空");
    this.finishedToday = Objects.requireNonNull(finishedToday, "完成量取数函数不能为空");
  }

  @Override
  public boolean isEligible(AssigneeType type, String assigneeId) {
    if (!delegate.isEligible(type, assigneeId)) {
      return false;
    }
    if (type != AssigneeType.USER) {
      return true;
    }
    long done;
    try {
      Long counted = finishedToday.apply(assigneeId);
      if (counted == null || counted < 0) {
        return false;
      }
      done = counted;
    } catch (RuntimeException e) {
      return false;
    }
    return done < config.maxPerShift();
  }
}
