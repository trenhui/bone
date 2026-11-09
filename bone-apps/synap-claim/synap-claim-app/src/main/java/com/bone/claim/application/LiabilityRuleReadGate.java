package com.bone.claim.application;

import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.adjustment.engine.RuleGap;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.model.liability.LiabilityReadMode;
import com.bone.claim.domain.model.liability.RuleValueDiff;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 责任规则读开关与双轨差异出口（M2 ③ 3a）。
 *
 * <p><b>为什么需要这个类而不是在领域里读配置</b>：{@code domain} 零框架依赖是硬约束。开关的读取、 聚合 JSON
 * 的解析、差异的日志与计数，三件事都属于"与外界打交道"，因此都留在应用层； 领域只接收已解析好的 {@code LiabilityRuleJsonView} 与一个显式的模式枚举。
 *
 * <p><b>默认关闭是承诺，不是保守</b>：关闭时 {@link #attach} 直接返回，连解析都不做—— 这保证了"开关关闭 ⇒ 零行为变更"可以被测出来（拿一段非法 JSON
 * 也不会抛）， 而不是只能靠"我觉得没影响"。
 *
 * <p><b>差异只记录，不阻断</b>：本类不抛业务异常、不改金额。若在这里拦截，等于给默认关闭的开关 装上了一个只在打开时才引爆的失败模式——那会让任何人都倾向于永远不打开它。
 */
@Component
@Slf4j
public class LiabilityRuleReadGate {

  /** 读开关：默认 false。生产打开须先有双轨比对证据（本类产出的日志与计数）。 */
  public static final String PROPERTY = "bone.claim.liability.rule-json-read.enabled";

  private final boolean enabled;

  /** 累计差异条数（进程内计数）：日志会被采样丢弃，计数不会，两者互为印证。 */
  private final AtomicLong diffCount = new AtomicLong();

  public LiabilityRuleReadGate(
      @Value("${bone.claim.liability.rule-json-read.enabled:false}") boolean enabled) {
    this.enabled = enabled;
  }

  public boolean enabled() {
    return enabled;
  }

  /** 累计差异条数（供观测与测试断言）。 */
  public long diffCount() {
    return diffCount.get();
  }

  /**
   * 按开关挂载读侧视图。
   *
   * <p>关闭时<b>不解析</b>：这是"零行为变更"里最难证明的一条，故用"非法 JSON 不抛异常"来钉住它。
   *
   * @throws com.bone.claim.domain.model.adjustment.engine.RuleGap 开关打开且 JSON 非法/版本未知/含未知键 —— 读取期
   *     fail-closed，不得静默回落旧路径
   */
  public void attach(AdjLiability liability) {
    if (!enabled || liability == null) {
      return;
    }
    try {
      liability.attachRuleRead(
          LiabilityRuleJsonCodec.decodeView(liability.getLiabilityRuleJson()),
          LiabilityReadMode.DUAL_READ);
    } catch (RuleGap gap) {
      // 受控拒绝而不是 500：读侧判据失败是「这一版配置读不出来」，属业务可解释的失败，
      // 必须带键名/版本号交给编排，而不是让栈信息替业务说话。
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_LIABILITY_INVALID_ARGUMENT, gap.detail());
    }
  }

  /**
   * 把本次装配产生的差异写成结构化日志并计数。
   *
   * <p>日志字段固定为「键 / 责任 UUID / 版本 / 旧标量值 / JSON 值」：这五项是事后区分 "配置写错了"与"读路径算错了"的最小集合，缺任一项都无法归因。
   */
  public void logDiffs(AdjLiability liability) {
    if (!enabled || liability == null) {
      return;
    }
    List<RuleValueDiff> diffs = liability.drainRuleDiffs();
    for (RuleValueDiff diff : diffs) {
      long total = diffCount.incrementAndGet();
      log.warn(
          "liability_rule_json 双轨差异 key={} liability={} version={} legacy={} json={} total={}",
          diff.key(),
          diff.liabilityUuid(),
          diff.liabilityVersion(),
          diff.legacyValue(),
          diff.jsonValue(),
          total);
    }
  }
}
