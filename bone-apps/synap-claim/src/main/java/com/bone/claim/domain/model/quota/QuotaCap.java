package com.bone.claim.domain.model.quota;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 多维控额的取值原语（P0 头号正确性债务的领域基座）。
 *
 * <p>参考实现的"多维控额"语义：一笔理算在<b>各层配置的剩余额度</b>中取最小值作为实际可用上限—— 例如「险种额度」是这一类责任共享的上限、「责任额度」是单条责任的上限，二者同时配置时
 * 实际可赔额 = min(险种剩余, 责任剩余)。本仓一期只把两层额度都配进来了，<b>引擎尚未消费这个 min</b> （见 {@code ClaimCoverage} 的
 * javadoc），故先在此把"取各层剩余最小值"的纯计算收敛到单一实现源， 引擎接线时直接调用、不在多处各写一遍（与 {@code QuotaProjection#rebuild} 同一纪律）。
 *
 * <p>约定：某一层未配置额度（{@code null}）视为"该层不封顶"，在 min 中被忽略； 仅当所有层都不封顶时才返回"不限"。这与"配了才生效"、避免把未配置误当 0 赔一致。
 */
public final class QuotaCap {

  private QuotaCap() {}

  /**
   * 取各层剩余额度的最小值。{@code null} 视为该层不封顶、参与 min 时被忽略。
   *
   * @return 实际可用的剩余上限；所有层都为 {@code null} 时返回 {@link Optional#empty()}（表示不限）。
   */
  public static Optional<BigDecimal> minRemaining(BigDecimal... layerRemainings) {
    BigDecimal floor = null;
    for (BigDecimal r : layerRemainings) {
      if (r == null) {
        continue;
      }
      if (floor == null || r.compareTo(floor) < 0) {
        floor = r;
      }
    }
    return Optional.ofNullable(floor);
  }
}
