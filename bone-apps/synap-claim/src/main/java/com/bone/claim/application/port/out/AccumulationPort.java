package com.bone.claim.application.port.out;

import com.bone.claim.domain.model.adjustment.Accumulation;

/**
 * 跨案累计读侧端口（A6 理算 ⑩ 的唯一取数入口）。
 *
 * <p><b>为什么是端口而不是让理算直接查仓储</b>：理算聚合必须是无副作用的纯计算（"同输入同输出"是理算可复算的合规硬要求）。 累计量由本端口在读侧取齐后<b>显式传入</b>
 * {@code Adjustment}，缺失即编译不过。
 *
 * <p><b>唯一账本是 {@code t_quota_account}</b>（三值模型），不再另立"累计表"。 理由：多一本账就多一次对账，而"四本账对不上"正是这类系统最难查的资金事故。
 * 责任账（{@code LIABILITY_CAP}，按 {@code liability_uuid} 分行）既承载责任限额的 {@code claimed+frozen}， 也承载年度免赔的
 * {@code deductible_used}——占额与免赔消抵在<b>同一个账户</b>内完成，天然守恒。
 *
 * <p><b>缺账户即 {@link Accumulation#MISSING}，不得伪造"已用 0"</b>： 未开户与"确认为零"在返回值上无法区分，若返回 {@code
 * (0,0,true)}，次案就会在账本缺失的情况下按满额赔付。 端口把区分责任交给调用方：A6 只标记、A7 拒绝占额。
 */
public interface AccumulationPort {

  /**
   * 读取某被保人在某保单年度、某责任下的累计用量。
   *
   * @param tenantId 租户（显式入参，便于日志/告警定位；仓储隔离由 {@code @TenantScope(AUTO)} 在租户上下文内另行强制）
   * @param insuredIdHash 假名化被保人
   * @param policyNo 保单号
   * @param planId 计划 ID
   * @param quotaYear 额度年度
   * @param liabilityUuid 责任 UUID（责任账的分行键）
   * @return 累计快照；未解析到责任账时返回 {@link Accumulation#MISSING}（{@code available=false}）
   */
  Accumulation load(
      long tenantId,
      String insuredIdHash,
      String policyNo,
      long planId,
      int quotaYear,
      String liabilityUuid);
}
