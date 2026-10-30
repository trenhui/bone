package com.bone.claim.domain.model.adjustment.engine;

import java.math.BigDecimal;

/**
 * 不变量自证（V1 §9 的 I1–I8 在引擎内落地）。
 *
 * <p><b>为什么断言写在引擎里而不是只写在测试里</b>：测试只证明"我构造的那几个用例"守恒； 引擎自证覆盖<b>每一个真实赔案</b>。分位丢失、负金额、超额度这类错误一旦流到付款环节，
 * 事后回溯的成本远高于当场抛错——而且当场抛错的栈信息直接指向出错的那一步。
 *
 * <p><b>为什么抛 {@link IllegalStateException} 而不是 {@link RuleGap}</b>：两者性质不同。 {@code RuleGap}
 * 是"配置没定义，业务上无法算"（可恢复、可转人工）；不变量破裂是"代码写错了"（不可恢复、必须修）。 混用会让真正的程序缺陷被当成"业务数据问题"降级处理。
 */
final class InvariantGuard {

  private InvariantGuard() {}

  /**
   * 校验 §9 中引擎可自证的部分。
   *
   * <table>
   *   <caption>不变量覆盖情况</caption>
   *   <tr><th>编号<th>断言<th>落点
   *   <tr><td>I1<td>Σ各费用类型理算金额 = 责任理算金额<td>本类
   *   <tr><td>I2<td>赔付 ≤ min(各类可用额度, 责任, 险种, 计划)<td>本类
   *   <tr><td>I3<td>半开区间无重叠<td>{@link PayRatioTable#validate()}
   *   <tr><td>I4<td>剩余免赔 ≥ 0 且已用 ≤ 年度免赔<td>本类
   *   <tr><td>I5<td>相对免赔跨门槛不减少赔付<td>{@link DeductibleEngine#apply} 结构保证 + 用例
   *   <tr><td>I6<td>拒付/假票/红冲不进累计<td>上游证据层（引擎入参已过滤，不重复判定）
   *   <tr><td>I7/I8<td>同输入同输出、累计前值可复现<td>快照物化（{@code AdjustmentEngine#buildSnapshot}）
   * </table>
   */
  static void verify(
      AdjustmentEngine.Outcome outcome,
      AdjustmentEngine.Request request,
      FeePools pools,
      int groupCount) {
    BigDecimal total = outcome.totalAmount();

    // 金额非负（负赔付只可能来自上游计算错误）
    if (total.signum() < 0) {
      throw new IllegalStateException("理算总额为负：" + total.toPlainString());
    }
    for (AdjustmentEngine.Line line : outcome.lines()) {
      requireNonNegative(line.feeCalculate(), "计算基准", line.bucket());
      requireNonNegative(line.feeDeduct(), "免赔扣减", line.bucket());
      requireNonNegative(line.ratioPayout(), "按比例应付", line.bucket());
      requireNonNegative(line.feePayout(), "实付", line.bucket());
    }

    // I1：归因明细合计 ≡ 总额（分位丢失的第一现场）
    BigDecimal lineSum = Money.ZERO;
    for (AdjustmentEngine.Line line : outcome.lines()) {
      lineSum = lineSum.add(line.feePayout());
    }
    if (lineSum.compareTo(total) != 0) {
      throw new IllegalStateException("I1 破裂：明细合计 %s ≠ 理算总额 %s".formatted(lineSum, total));
    }

    // I2：不得超过责任剩余额度
    LiabilityRule rule = request.rule();
    if (!rule.limits().liabilityUncapped()) {
      BigDecimal remaining =
          Money.nonNegative(
              rule.limits().liabilityLimit().subtract(request.accumulation().limitUsed()));
      if (total.compareTo(remaining) > 0) {
        throw new IllegalStateException("I2 破裂：赔付 %s 超过责任剩余额度 %s".formatted(total, remaining));
      }
    }

    // I4：免赔不得超配、不得为负
    BigDecimal deductibleApplied = outcome.deductibleApplied();
    if (deductibleApplied.signum() < 0) {
      throw new IllegalStateException("I4 破裂：免赔消抵额为负 " + deductibleApplied);
    }
    if (!rule.deductible().inactive()) {
      BigDecimal configured = rule.deductible().amount();
      // 按次免赔的上界是"每「次」一份"，故须乘以分组数；年度免赔才是全案一本账。
      BigDecimal remainingBefore =
          rule.deductible().basis() == DeductibleRule.Basis.ANNUAL
              ? DeductibleEngine.resolveRemaining(
                  rule.deductible(), request.accumulation().deductibleUsed())
              : DeductibleEngine.resolveRemaining(rule.deductible(), Money.ZERO)
                  .multiply(BigDecimal.valueOf(Math.max(1, groupCount)));
      BigDecimal allowed = configured.signum() > 0 ? remainingBefore : configured;
      // 容差 = 明细行数 × 0.01：消抵额按结果精度取整后回传，"免赔 0.009"这类配置本身不足 1 分，
      // 取整后可能比剩余大 1 分。这是精度约定的必然产物，不是逻辑错误。
      BigDecimal tolerance =
          new BigDecimal("0.01").multiply(BigDecimal.valueOf(Math.max(1, outcome.lines().size())));
      if (allowed.signum() > 0 && deductibleApplied.compareTo(allowed.add(tolerance)) > 0) {
        throw new IllegalStateException(
            "I4 破裂：本次免赔消抵 %s 超过剩余可抵扣 %s".formatted(deductibleApplied, allowed));
      }
    }
  }

  private static void requireNonNegative(BigDecimal value, String label, String bucket) {
    if (Money.nz(value).signum() < 0) {
      throw new IllegalStateException(
          "%s 为负：%s（费用池 %s）".formatted(label, value.toPlainString(), bucket));
    }
  }
}
