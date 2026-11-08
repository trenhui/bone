package com.bone.claim.domain.model.quota;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 额度账户投影的<b>按流水重算</b>（{@code frozen / claimed / deductible_used}），以及投影与事实的比对。
 *
 * <p><b>为什么必须只有一份实现</b>：账户上的四个计数是<b>投影</b>，{@code t_quota_transaction} 才是事实 （§11.7 不变量
 * 1）。投影的用途有两处，且两处必须同口径：
 *
 * <ul>
 *   <li><b>自愈</b>（写路径）：账户冻结额不足以支撑本单释放时，按流水重算覆盖投影，否则合法释放被永久拒掉、 结算单卡死在冻结态、钱出不来（见 {@code
 *       QuotaApplicationService#reconcileProjectionIfDeficit}）。
 *   <li><b>对账</b>（侦测）：T+1 逐账户校验"投影 = Σ 流水"，不一致即 P0。
 * </ul>
 *
 * <p>两处若各写一遍聚合口径，就会出现"自愈按 A 口径修、对账按 B 口径判"——修完仍红，或红了却修不掉。 故聚合口径收在本类，调用方只消费结果。
 *
 * <p><b>口径要点</b>：
 *
 * <ul>
 *   <li>{@code SOFT} 增加冻结；{@code HARD} 按 {@code max(0, after − before)} 计入（新版"SOFT 起手 + HARD 升级"
 *       增量为 0，老版 HARD 直接起手则为真实占用，见 {@link #hardDelta}）；{@code PAID} 把冻结转成已付； {@code RELEASE}
 *       归还冻结；{@code REFUND} 回冲已付；{@code DEDUCTIBLE} 只动年免赔；{@code DAILY}
 *       不动任何余额（它是"某天赔了多少"的事实，不参与守恒）——<b>逐类型穷举而非按 direction 二分</b>， 因为 {@code ROLLBACK} 方向下同时混着
 *       RELEASE（退冻结）与 REFUND（退已付），按方向聚合必然串账。
 *   <li>按结算单聚合后<b>只累加净额为正的单</b>：净额为负 = 越权释放 / 无对应占用的归还，它没有占用过额度，
 *       不应在投影里抵减别人的占用（抵减即"把别人的占用冲掉"，池被抽干后他人合法释放被拒）。
 * </ul>
 *
 * @param frozen 冻结占用投影（{@code Σ 各结算单净冻结占用，只计正数}）
 * @param claimed 已扣减投影（{@code Σ 各结算单净已付，只计正数}）
 * @param deductibleUsed 年免赔已用投影（{@code Σ 各结算单净消抵，只计正数}）
 */
public record QuotaProjection(BigDecimal frozen, BigDecimal claimed, BigDecimal deductibleUsed) {

  private static final BigDecimal ZERO = new BigDecimal("0.00");

  /**
   * 按流水事实重算投影。
   *
   * @param ledger 账户全部流水（append-only 事实；顺序无关，聚合是交换的）
   */
  public static QuotaProjection rebuild(List<QuotaTransaction> ledger) {
    Map<String, BigDecimal> held = new LinkedHashMap<>();
    Map<String, BigDecimal> paid = new LinkedHashMap<>();
    Map<String, BigDecimal> deductible = new LinkedHashMap<>();
    if (ledger != null) {
      for (QuotaTransaction t : ledger) {
        if (t == null) {
          continue;
        }
        // 结算单号为空的流水<b>单独成组</b>而不是丢弃：丢弃会让"账户上有一笔没归属的占用"在投影里凭空消失，
        // 自愈时被抹掉、对账时判成一致——错得安静。归到哨兵组后它仍计入池，只是不参与单笔归属判定。
        String key = t.getSettlementNo() == null ? "" : t.getSettlementNo();
        String type = t.getTxnType() == null ? "" : t.getTxnType();
        BigDecimal amount = t.getAmount() == null ? ZERO : t.getAmount();
        switch (type) {
          case "SOFT" -> held.merge(key, amount, BigDecimal::add);
          case "HARD" -> held.merge(key, hardDelta(t), BigDecimal::add);
          case "PAID" -> {
            held.merge(key, amount.negate(), BigDecimal::add);
            paid.merge(key, amount, BigDecimal::add);
          }
          case "RELEASE" -> held.merge(key, amount.negate(), BigDecimal::add);
          case "REFUND" -> paid.merge(key, amount.negate(), BigDecimal::add);
          case "DEDUCTIBLE" -> deductible.merge(
              key, "OCCUPY".equals(t.getDirection()) ? amount : amount.negate(), BigDecimal::add);
          default -> {
            // DAILY（日用量）与将来新增的"只记事实、不动余额"的类型：不参与守恒投影。
          }
        }
      }
    }
    return new QuotaProjection(sumPositive(held), sumPositive(paid), sumPositive(deductible));
  }

  /**
   * 投影与账户现有值的差异（空集 = 一致）。
   *
   * <p>四个计数逐个比对，<b>差异逐项列出而不是只回一个布尔</b>：对账差异单要能回答"哪一列错了、错多少"， 只有一个"不一致"的话，风控拿到的仍是"账上有问题"，等于让人自己去翻流水。
   *
   * <p><b>{@code cap_used} 的期望值是 {@code claimed}</b>：年封顶已用 = 已确认赔付（PERSONAL 账户的 {@code
   * quota_initial} 就是年封顶，责任账的同名计数同理）。它是"封顶口径"的读模型冗余列——今天与 {@code claimed}
   * 恒等，故本类不单独聚合它，而是据此比对；一旦将来出现"不计封顶的赔付"， 必须同批改这里与 {@code cap_used} 的维护点，否则会变成两套口径。
   *
   * @param account 账户当前投影
   */
  public List<Drift> driftAgainst(QuotaAccount account) {
    List<Drift> drifts = new ArrayList<>();
    if (account == null) {
      return drifts;
    }
    addIfDifferent(drifts, "quota_frozen", account.getQuotaFrozen(), frozen);
    addIfDifferent(drifts, "quota_claimed", account.getQuotaClaimed(), claimed);
    addIfDifferent(drifts, "deductible_used", account.getDeductibleUsed(), deductibleUsed);
    addIfDifferent(drifts, "cap_used", account.getCapUsed(), claimed);
    return drifts;
  }

  /**
   * {@code HARD} 流水的真实占用增量（{@code max(0, after − before)}）。
   *
   * <p>新版"SOFT 起手 + HARD 升级"下 before==after ⇒ 增量为 0（不重复计占用，防双冻结）；早期版本用 HARD 直接起手时 before≠after ⇒
   * 增量为真实占用额。{@code before}/{@code after} 缺失时按 0 处理（宁可少算也不凭空计占用）。
   *
   * <p>公开是因为<b>单笔归属判定</b>（本结算单还占多少冻结）与投影重算必须用同一口径，否则两者互相打架。
   */
  public static BigDecimal hardDelta(QuotaTransaction t) {
    BigDecimal before = t.getBeforeAmount();
    BigDecimal after = t.getAfterAmount();
    if (before == null || after == null) {
      return ZERO;
    }
    return after.subtract(before).max(ZERO);
  }

  /** 各结算单净额求和（只计正数：净额为负 = 越权释放 / 无对应占用的归还，不应占用投影）。 */
  private static BigDecimal sumPositive(Map<String, BigDecimal> perSettlement) {
    BigDecimal total = ZERO;
    for (BigDecimal net : perSettlement.values()) {
      if (net != null && net.signum() > 0) {
        total = total.add(net);
      }
    }
    return total;
  }

  private static void addIfDifferent(
      List<Drift> drifts, String field, BigDecimal stored, BigDecimal rebuilt) {
    BigDecimal left = stored == null ? ZERO : stored;
    BigDecimal right = rebuilt == null ? ZERO : rebuilt;
    if (left.compareTo(right) != 0) {
      drifts.add(new Drift(field, left, right));
    }
  }

  /**
   * 单列投影漂移。
   *
   * @param field 列名（{@code quota_frozen / quota_claimed / deductible_used / cap_used}）
   * @param projection 账户上的现存值（对账差异单的"本方金额"）
   * @param ledger 按流水重算出的值（对账差异单的"对方金额"）
   */
  public record Drift(String field, BigDecimal projection, BigDecimal ledger) {}
}
