package com.bone.claim.infrastructure.rule;

import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.RuleEnginePort;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * 规则/理算引擎适配器（一期本地确定性桩）。
 *
 * <p><b>诚实标注</b>：本桩<b>未执行任何规则</b>，故 {@code available=false}——「无冲突/无风险」在此处是默认值而非结论，
 * 不得被下游当作「已核责且干净」。决策图据 {@code available} 把规则这一路并入反欺诈硬闸的 fail-closed 判定（不可用 即无法确认安全 → 升级人工），与视觉 E1 /
 * 图计算 E2 的可用性语义保持一致。
 *
 * <p>核责默认无冲突、风控通过、规则置信度 0.90；理算回显申请金额（免赔/封顶/责任比例属二期接责任配置）。 替换只动本类，二期接 QLExpress 热更 + 灰度 +
 * 回归门禁真实引擎（bone-ai C2，§7.3）；届时 {@code available} 置 {@code true}。
 *
 * <p><b>F1（2026-10-29 修正 10-28 误判，见「生产就绪完善/13-理算引擎三重路径收敛与护栏移植」）</b>：{@link #calculatePayout} 返回的
 * <b>仅是「分诊估算额」（= 申请金额）</b>，仅供决策图 {@code Adjust} 节点做档位判断（大额 → 人工复核档），<b>绝不进入资金链路</b>、<b>绝非结算赔付额</b>。
 * 真实结算金额由领域聚合 {@code Adjustment.calculate(rows, liability)} 经 A6 理算（{@code
 * IndemnityApplicationService.calculate} 调用，产出即 FROZEN）算定，并由 A7 结算（{@code
 * SettlementApplicationService.resolveAmount} 读取最新 FROZEN {@code Adjustment} 的 {@code
 * totalAmount}）取用——<b>资金链路已接线、AI 不碰钱</b>。10-28 诊断称" {@code Adjustment.calculate} 在生产 0 调用 =
 * 结算未接线"系当时 grep 漏检 {@code IndemnityApplicationService} 的误判，已撤销。本适配器估算额仅作分诊档位输入， 禁止当赔付额。二期接 bone-ai
 * C2（§7.3）真实引擎时，应装配能取数并委托 {@code Adjustment} 的真实适配器替换本类。
 */
@Component
public class LocalRuleEnginePortAdapter implements RuleEnginePort {

  private static final BigDecimal RULE_CONFIDENCE = new BigDecimal("0.90");

  @Override
  public RuleCheckResult check(ClaimContextPort.ClaimContextView ctx) {
    // 验真不通过（FAIL/UNVERIFIABLE）即反欺诈硬闸不过（R-FRAUD-06 从严）
    boolean fraudGatePassed = ctx.evidenceAllVerifyPassed();
    // available=false：规则未真实执行，下列布尔为默认值，不可当作已核责结论
    return new RuleCheckResult(false, false, false, fraudGatePassed, RULE_CONFIDENCE);
  }

  @Override
  public BigDecimal calculatePayout(ClaimContextPort.ClaimContextView ctx) {
    // F1：返回分诊估算额（= 申请金额），仅供决策图分诊档位判断（大额→人工）。
    // 注意：此为估算额，不是结算赔付额；真实结算必须走 Adjustment.calculate(rows, liability)。
    return ctx.claimAmount();
  }
}
