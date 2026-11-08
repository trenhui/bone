package com.bone.claim.domain.model.settlement;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 资金阶段链校验（§11.7 不变量 3「阶段单调」）。
 *
 * <p><b>为什么需要单独校验</b>：{@code SettlementOrder} 聚合根只在内存里推进状态机，落库后的事实是 {@code settle_funding_event}
 * 的一串 {@code stage}。聚合根保证不了已经落库的流水：手工补数、断点重放、幂等键被绕过、 迁移脚本写错，都会留下一条「PAID 之后又出现
 * SOFT_RESERVED」的序列——而每一张结算单单看字段都「合法」， 只有把流水按时间串起来才看得出钱的状态被倒拨过。对账 PAYMENT 通道此前只比「末条 = 结算单当前阶段」，
 * 恰好对逆向序列无感（末条若是新追加的 SOFT，反而与结算单同步改过之后依然相等）。
 *
 * <p><b>为什么放在领域层而不是对账服务里</b>：合法迁移表就是 {@link FundingStage} 的语义（§6.4 状态机）， 把它写进 application 层的 if
 * 链，等于给状态机造了第二份定义；聚合根哪天加一条分支，对账仍按旧表判—— 判的已经不是同一件事。
 *
 * <p><b>不校验「首条必须是 SOFT_RESERVED」</b>：历史版本用 {@code HARD} 直接起手（见 {@code QuotaProjection#hardDelta}
 * 对同款历史数据的处理），老流水首条不是 SOFT 属于<b>已知历史形态</b>， 不判违规。判「没有前序的孤立阶段」只会把历史数据变成长期假红，而假红会让真红被忽略。
 */
public final class FundingStageChain {

  /** 合法后继（含「同阶段视为重复追加」，故自身不在后继集合里）。 */
  private static final Map<FundingStage, Set<FundingStage>> SUCCESSORS =
      new EnumMap<>(FundingStage.class);

  static {
    // SOFT → REVIEW_FREEZE：大额审核通过时直接进复核冻结（auditPass 只有这一条路径，不经 HARD）
    SUCCESSORS.put(
        FundingStage.SOFT_RESERVED,
        EnumSet.of(
            FundingStage.REVIEW_FREEZE,
            FundingStage.HARD_RESERVED,
            FundingStage.RELEASED,
            FundingStage.FAILED));
    SUCCESSORS.put(
        FundingStage.HARD_RESERVED,
        EnumSet.of(
            FundingStage.REVIEW_FREEZE,
            FundingStage.PAID,
            FundingStage.RELEASED,
            FundingStage.FAILED));
    // REVIEW_FREEZE → HARD_RESERVED 是「复核放行」，不是逆向（§6.4 / FundingStage 类注释）
    SUCCESSORS.put(
        FundingStage.REVIEW_FREEZE,
        EnumSet.of(FundingStage.HARD_RESERVED, FundingStage.RELEASED, FundingStage.FAILED));
    SUCCESSORS.put(FundingStage.PAID, EnumSet.of(FundingStage.REFUNDED));
    SUCCESSORS.put(FundingStage.RELEASED, EnumSet.noneOf(FundingStage.class));
    SUCCESSORS.put(FundingStage.FAILED, EnumSet.noneOf(FundingStage.class));
    SUCCESSORS.put(FundingStage.REFUNDED, EnumSet.noneOf(FundingStage.class));
  }

  private FundingStageChain() {}

  /** 违规种类。两种成因与处置不同，故不合并成一个布尔。 */
  public enum BreachKind {
    /** 非法迁移：{@code from → to} 不在状态机里（含终态后追加、同阶段重复追加）。 */
    ILLEGAL_TRANSITION,
    /** 事件时间倒序：append-only 表里出现比前一条更早的事件（补数/乱序重放）。 */
    OUT_OF_ORDER
  }

  /**
   * 一处违规。
   *
   * @param kind 违规种类
   * @param from 前序阶段（忽略大小写不存在；{@code OUT_OF_ORDER} 时为 {@code EVENT_TIME} 语义由调用方解释）
   * @param to 后序阶段（{@code OUT_OF_ORDER} 时为 {@code OUT_OF_ORDER} 占位，见 {@link #check}）
   */
  public record Breach(BreachKind kind, String from, String to) {}

  /** 合法后继（空集 = 终态）。未识别的阶段返回空集（保守：它不能通向任何地方）。 */
  public static Set<FundingStage> successors(FundingStage from) {
    if (from == null) {
      return Collections.emptySet();
    }
    return SUCCESSORS.getOrDefault(from, Collections.emptySet());
  }

  /** 单步是否合法（供聚合根以外的地方复用时保持同一张表）。 */
  public static boolean legal(FundingStage from, FundingStage to) {
    return to != null && successors(from).contains(to);
  }

  /**
   * 校验一条结算单的完整资金流水（按落库顺序，即 {@code idx_settlement (settlement_id, id)} 的顺序）。
   *
   * <p>空列表返回空集：<b>没有流水</b>是「本方缺失」，由 PAYMENT 通道的 {@code MISSING_LOCAL} 承载——
   * 同一条数据缺陷不由两个判据重复报（否则一次缺流水会变成两行差异，派单时会被当成两件事）。
   *
   * @param events 该结算单的全部资金流水（append-only，按 id 升序）
   */
  public static List<Breach> check(List<FundingEvent> events) {
    List<Breach> breaches = new ArrayList<>();
    if (events == null || events.isEmpty()) {
      return breaches;
    }
    FundingStage prev = null;
    Instant prevTime = null;
    for (FundingEvent event : events) {
      if (event == null) {
        continue;
      }
      FundingStage cur;
      try {
        cur = event.stageEnum();
      } catch (IllegalArgumentException ex) {
        // 库里出现枚举外的 stage：不是「未来新阶段」就是脏数据，两种都不该被静默当成合法前序
        breaches.add(
            new Breach(
                BreachKind.ILLEGAL_TRANSITION, prev == null ? "" : prev.name(), safeStage(event)));
        prev = null;
        prevTime = null;
        continue;
      }
      if (prev != null && !legal(prev, cur)) {
        breaches.add(new Breach(BreachKind.ILLEGAL_TRANSITION, prev.name(), cur.name()));
      }
      Instant curTime = event.getEventTime();
      if (prevTime != null && curTime != null && curTime.isBefore(prevTime)) {
        breaches.add(new Breach(BreachKind.OUT_OF_ORDER, "EVENT_TIME", "OUT_OF_ORDER"));
      }
      prev = cur;
      prevTime = curTime == null ? prevTime : curTime;
    }
    return breaches;
  }

  private static String safeStage(FundingEvent event) {
    String stage = event.getStage();
    if (stage == null) {
      return "UNKNOWN";
    }
    // remote_status 列为 VARCHAR(32)：脏值可能很长，截断到列宽以免落库失败把整轮对账回滚
    return stage.length() <= 32 ? stage : stage.substring(0, 32);
  }
}
