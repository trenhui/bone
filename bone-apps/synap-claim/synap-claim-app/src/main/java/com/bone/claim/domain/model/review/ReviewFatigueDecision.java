package com.bone.claim.domain.model.review;

import com.bone.claim.domain.model.config.ReviewFatigueConfig;
import java.util.Map;

/**
 * 审核疲劳只读评估结论（纯值对象）。
 *
 * <p>{@code sensitiveEligible} 只是工作台提示：没有可信的服务端班次结束事实前，不宣称实现了班末硬排除。 {@code rejected}
 * 直接透出生效配置的拒绝项，让「配了不生效」在上屏时可见。
 */
public record ReviewFatigueDecision(
    boolean overloaded, boolean restPrompt, boolean sensitiveEligible, ReviewFatigueConfig config) {

  public Map<String, String> rejected() {
    return config.rejected();
  }
}
