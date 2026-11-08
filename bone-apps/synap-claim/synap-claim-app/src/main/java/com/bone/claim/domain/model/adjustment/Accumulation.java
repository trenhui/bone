package com.bone.claim.domain.model.adjustment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * 跨案累计快照（理算输入，理算链第 ⑩ 环「跨案累计」的数据源）。
 *
 * <p><b>为什么必须由外部传入而不是内核自己查</b>：理算聚合是纯函数式的金额计算入口（AI 不碰钱、无副作用），
 * 读仓储会把它变成"依赖基础设施的可变对象"，既破坏"同输入同输出"的可复算性，也让单测必须起容器。 累计量由应用层经 {@code AccumulationPort}
 * 读入后<b>显式传入</b>——缺失即编译不过，杜绝"默认 0"式的隐形口径。
 *
 * <p><b>三本账，缺一不可</b>（对标 pk-tpa {@code PersonalQuota} 与平安"保单级聚合账"）：
 *
 * <ul>
 *   <li>{@code deductibleUsed}：年度免赔<b>已消抵</b>额（R-DED-03）。次案起免赔只扣<b>剩余</b>部分， 否则每案各扣一次全额 ⇒ 系统性少赔。
 *   <li>{@code limitUsed}：责任限额<b>已占用</b>额（含 {@code claimed} 已付 + {@code frozen} 预占）。
 *       次案起限额只按<b>剩余</b>截断，否则每案各用满额 ⇒ 系统性超赔。
 *   <li>{@code dailyUsed}：<b>同一业务日</b>已占用的赔付额（Q-05 日赔付限额）。窗口由 {@code dailyWindow}
 *       给出——只记一个数不记哪天毫无意义：跨日必须归零，否则第二天一开门就是"今天已经赔满了"。
 * </ul>
 *
 * <p><b>{@code available} 是 fail-closed 信号，不是装饰</b>：{@code false} 表示读侧<b>没解析到责任账</b> （未开户 / 维度不匹配 /
 * 数据缺失），此时"已用 = 0"是<b>未知</b>而不是"确认为零"。 理算（A6）据此只标记不阻断——结果仍要给人看；结算（A7）据此<b>拒绝占额</b>——
 * 资金路径不允许在账实不明的账本上记账。把两者写成同一个"抛异常"会让理算在缺数据时直接不可用， 写成同一个"静默按 0"则会静默超赔；这里是唯一既可用又可审计的切分。
 *
 * <p><b>{@code dailyWindow} 与日限额的关系</b>：日用量是"某个业务日"的量，窗口日与本案业务日不一致时必须
 * 判明是"那天还没赔过（0）"还是"账本记的是更晚的一天（本案发票日期倒挂）"——前者取 0 是对的，后者取 0 就是超赔。该判定<b>不在本类做</b>（本类只是数据载体），由 {@code
 * AdjustmentEngine} 用 本案可证的业务日比对窗口后决定，见 {@link #remainingDaily}。
 */
public record Accumulation(
    BigDecimal deductibleUsed,
    BigDecimal limitUsed,
    BigDecimal dailyUsed,
    LocalDate dailyWindow,
    boolean available) {

  private static final BigDecimal ZERO = new BigDecimal("0.00");

  /** 首案 / 确有账本且已用为零：{@code available=true}，"已用 0"是<b>事实</b>而非缺失。 */
  public static final Accumulation FIRST = new Accumulation(ZERO, ZERO, ZERO, null, true);

  /** 读侧未解析到责任账：{@code available=false}，"已用 0"是<b>未知</b>，上层须 fail-closed。 */
  public static final Accumulation MISSING = new Accumulation(ZERO, ZERO, ZERO, null, false);

  public Accumulation {
    deductibleUsed = nz(deductibleUsed);
    limitUsed = nz(limitUsed);
    dailyUsed = nz(dailyUsed);
  }

  /**
   * 构造（空值按 0 处理；无日用量时窗口为 {@code null}）。
   *
   * @param deductibleUsed 年度免赔已消抵（可为 null）
   * @param limitUsed 责任限额已占用（可为 null）
   * @param available 读侧是否真正解析到责任账
   */
  public static Accumulation of(
      BigDecimal deductibleUsed, BigDecimal limitUsed, boolean available) {
    return new Accumulation(deductibleUsed, limitUsed, ZERO, null, available);
  }

  /**
   * 构造（含日用量）。
   *
   * @param dailyWindow 日用量的业务日；{@code null} 表示尚无任何按日占用
   * @param dailyUsed 该业务日已占用额（可为 null）
   */
  public static Accumulation of(
      BigDecimal deductibleUsed,
      BigDecimal limitUsed,
      BigDecimal dailyUsed,
      LocalDate dailyWindow,
      boolean available) {
    return new Accumulation(deductibleUsed, limitUsed, dailyUsed, dailyWindow, available);
  }

  /**
   * 剩余年度免赔 = max(0, 配置免赔 − 已消抵)。
   *
   * <p>钳到 0 而非负值：已消抵超过配置（历史误赔 / 配置下调）时，剩余只能是 0， 不允许"负免赔"反向放大赔付基数。
   */
  public BigDecimal remainingDeductible(BigDecimal configured) {
    return nz(configured).subtract(nz(deductibleUsed)).max(ZERO).setScale(2, RoundingMode.HALF_UP);
  }

  /**
   * 剩余责任限额 = max(0, 配置限额 − 已占用)。
   *
   * <p><b>仅当配置限额 &gt; 0 时有意义</b>：{@code limit=0} 在责任配置里表示"不封顶"， 调用方须先判 {@code limit.signum() > 0}
   * 再取本值——否则会把"不封顶"误判成"限额已耗尽 ⇒ 赔 0"。
   */
  public BigDecimal remainingLimit(BigDecimal configured) {
    return nz(configured).subtract(nz(limitUsed)).max(ZERO).setScale(2, RoundingMode.HALF_UP);
  }

  /**
   * 当日剩余可赔额 = max(0, 配置日限额 − 当日已占用)。
   *
   * <p>钳零同理：当日已用超过限额（限额下调 / 历史误赔）时只能赔 0，不允许负剩余反向放大。
   */
  public BigDecimal remainingDaily(BigDecimal configured) {
    return nz(configured).subtract(nz(dailyUsed)).max(ZERO).setScale(2, RoundingMode.HALF_UP);
  }

  private static BigDecimal nz(BigDecimal value) {
    return value == null ? ZERO : value;
  }
}
