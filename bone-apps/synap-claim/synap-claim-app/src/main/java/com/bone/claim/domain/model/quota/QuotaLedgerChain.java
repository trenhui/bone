package com.bone.claim.domain.model.quota;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 额度流水的<b>余额串链</b>校验（§11.7 不变量 1 ④：{@code after_amount} 首尾相接）。
 *
 * <p><b>为什么不是"整表按 id 串一条链"</b>（此前文档判定"不可做"，这里改判为"按列分链可做"，原因要写清）：{@code before_amount/after_amount}
 * 记录的是<b>三本不同的余额</b>，不是一本——{@code SOFT/RELEASE} 记冻结额、{@code PAID/REFUND} 记已扣减额、{@code DEDUCTIBLE}
 * 记年免赔已用，{@code DAILY} 两侧恒 0（只记"某天赔了多少"的事实）。 恰好这三本余额的 {@code txn_type}
 * 不相交，故<b>按余额分三条链</b>串。若强行只串一条链，{@code DAILY}（0→0）与 三个列之间的穿插 会让每一条都断，
 * 得到一整片永不消失的假红——假红比不查更糟：真差异会淹没在噪音里没人看。
 *
 * <p><b>跨列影响必须显式建模</b>：{@code PAID} 只<b>记录</b>已扣减额（before/after 取 {@code quota_claimed}），却同时把冻结额
 * 减掉同额。 如果只看"记录列"，冻结链会在每次付款后断一次。故本类的判定表列明"这笔流水额外动了哪一列、动多少"， 串链时同步推进该列的游标——这不是在重算投影（聚合口径仍只在 {@link
 * QuotaProjection} 里），而是在复现"这两列当时是怎么被同一条流水推着走的"。
 *
 * <p><b>首条取 before 为基线，不判"必须是 0"</b>：历史账户可能存在未入流水的期初占用（老版本 HARD 直接起手、迁移补数）， 判首条从 0
 * 起会把这些账户永久判红，而它们的问题正是"期初值没有流水依据"——这件事归"投影 = 事实"判据（{@code PROJECTION_DRIFT}）管，
 * 本类不重复报同一病根。同理：某列游标一旦变负（必然意味着有一笔归还没有对应占用），该列<b>自此停止串链</b>，因为此后的 {@code before}
 * 取自被钳过的账面值，再比下去只是在报同一条病根。
 *
 * <p>两类断链分开标注（{@code kind}），处置方向不同：
 *
 * <ul>
 *   <li>{@code CONTINUITY}：本条 {@code before} ≠ 上一条同列 {@code after} ⇒ 两笔操作读到了同一个旧值（并发丢失更新），
 *       或中间被一段没有流水的手工改数插进来。
 *   <li>{@code ARITHMETIC}：本条 {@code after} ≠ {@code before} ± 金额 ⇒ 金额与前后值算术不自洽（写错列、算错方向）。
 * </ul>
 *
 * @see QuotaProjection 由流水聚合投影（"值对不对"）；本类只判"链连不连"（"过程对不对"），两者不可互替
 */
public final class QuotaLedgerChain {

  /** 冻结额（{@code quota_account.quota_frozen}）。 */
  public static final String COL_FROZEN = "quota_frozen";

  /** 已扣减额（{@code quota_account.quota_claimed}）。 */
  public static final String COL_CLAIMED = "quota_claimed";

  /** 年免赔已用（{@code quota_account.deductible_used}）。 */
  public static final String COL_DEDUCTIBLE = "deductible_used";

  /** 断链类型。 */
  public enum BreakKind {
    /** 与上一条同列流水的 {@code after} 接不上。 */
    CONTINUITY,
    /** {@code after} 与 {@code before} 的差额不等于本笔金额（含方向）。 */
    ARITHMETIC
  }

  /**
   * 一处断链。
   *
   * @param column 发生断链的余额列
   * @param kind 断链类型
   * @param txnType 本条流水类型（定位用）
   * @param idempotencyKey 本条流水幂等键（定位用）
   * @param expected 链上应有的值（{@code CONTINUITY} 取上一条的 {@code after}，{@code ARITHMETIC} 取 {@code
   *     before} ± 金额）
   * @param recorded 流水实际记录的值
   */
  public record Break(
      String column,
      BreakKind kind,
      String txnType,
      String idempotencyKey,
      BigDecimal expected,
      BigDecimal recorded) {}

  /**
   * 一笔流水对<b>记录列</b>与<b>另一列</b>的影响。
   *
   * @param column 本条流水 {@code before/after} 所指的余额列
   * @param delta 本笔对该列的增量；{@code null} 表示不判算术（{@code HARD} 的增量由 {@code after − before} 定义，判定即同义反复）
   * @param otherColumn 本条流水顺带改动的另一列（无则 {@code null}）
   * @param otherDelta 对另一列的增量
   */
  private record Movement(
      String column, BigDecimal delta, String otherColumn, BigDecimal otherDelta) {}

  private static final BigDecimal ZERO = new BigDecimal("0.00");

  private QuotaLedgerChain() {}

  /**
   * 逐条串链（入参须按落账顺序，即 {@code t_quota_transaction} 的 {@code id} 升序）。
   *
   * @param ledger 某账户全部流水；{@code null} 或空返回空集
   * @return 断链清单（空集 = 全部相接）
   */
  public static List<Break> check(List<QuotaTransaction> ledger) {
    List<Break> breaks = new ArrayList<>();
    if (ledger == null || ledger.isEmpty()) {
      return breaks;
    }
    Map<String, BigDecimal> running = new LinkedHashMap<>();
    Set<String> anchored = new LinkedHashSet<>();
    Set<String> halted = new LinkedHashSet<>();
    for (QuotaTransaction txn : ledger) {
      if (txn == null) {
        continue;
      }
      String txnType = txn.getTxnType() == null ? "" : txn.getTxnType();
      BigDecimal amount = txn.getAmount() == null ? ZERO : txn.getAmount();
      Movement movement = movementOf(txn, txnType, amount);
      if (movement == null) {
        // DAILY 与"只记事实、不动余额"的类型：before/after 恒 0，串链对其无意义
        continue;
      }
      BigDecimal before = txn.getBeforeAmount();
      BigDecimal after = txn.getAfterAmount();
      String column = movement.column();
      if (before != null && after != null && !halted.contains(column)) {
        if (!anchored.contains(column)) {
          running.put(column, before); // 首条取 before 为基线（见类注释）
          anchored.add(column);
        }
        BigDecimal expected = nz(running.get(column));
        if (before.compareTo(expected) != 0) {
          breaks.add(
              new Break(
                  column,
                  BreakKind.CONTINUITY,
                  txnType,
                  txn.getIdempotencyKey(),
                  expected,
                  before));
        }
        if (movement.delta() != null && after.compareTo(before.add(movement.delta())) != 0) {
          breaks.add(
              new Break(
                  column,
                  BreakKind.ARITHMETIC,
                  txnType,
                  txn.getIdempotencyKey(),
                  before.add(movement.delta()),
                  after));
        }
        running.put(column, after);
        if (after.signum() < 0) {
          // 该列已无可信基线：后续 before 取自被钳过的账面值，再比只是重复报同一病根
          halted.add(column);
        }
      }
      if (movement.otherColumn() != null && !halted.contains(movement.otherColumn())) {
        BigDecimal moved = nz(running.get(movement.otherColumn())).add(movement.otherDelta());
        running.put(movement.otherColumn(), moved);
        if (moved.signum() < 0) {
          halted.add(movement.otherColumn());
        }
      }
    }
    return breaks;
  }

  /**
   * 判定表：本笔流水记录哪一列、增量多少、顺带改哪一列。
   *
   * <p>与 {@link QuotaProjection#rebuild} 的聚合口径必须同源（都读了 {@code txn_type}/{@code
   * direction}），但两者判的不是同一件事： 那边判"值 = Σ流水"，这边判"每条流水的前后值是否接得上"。改一处口径时另一处必须同步——写成两个 {@code switch}
   * 是刻意的：合并成一个会逼出一个"既算总额又判连续性"的四不像。
   */
  private static Movement movementOf(QuotaTransaction txn, String txnType, BigDecimal amount) {
    return switch (txnType) {
      case "SOFT" -> new Movement(COL_FROZEN, amount, null, ZERO);
      case "HARD" -> new Movement(COL_FROZEN, null, null, ZERO);
      case "PAID" -> new Movement(COL_CLAIMED, amount, COL_FROZEN, amount.negate());
      case "RELEASE" -> new Movement(COL_FROZEN, amount.negate(), null, ZERO);
      case "REFUND" -> new Movement(COL_CLAIMED, amount.negate(), null, ZERO);
      case "DEDUCTIBLE" -> new Movement(
          COL_DEDUCTIBLE,
          "OCCUPY".equals(txn.getDirection()) ? amount : amount.negate(),
          null,
          ZERO);
      default -> null;
    };
  }

  private static BigDecimal nz(BigDecimal value) {
    return value == null ? ZERO : value;
  }
}
