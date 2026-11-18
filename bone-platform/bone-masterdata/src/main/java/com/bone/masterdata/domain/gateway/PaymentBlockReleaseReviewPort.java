package com.bone.masterdata.domain.gateway;

import java.time.Instant;

/**
 * 解除付款冻结的<b>人工复核留痕</b>端口。
 *
 * <p>spec:83 要求「付款冻结只能由外部财务事件<b>加人工复核留痕</b>解除」。只校验事件来源 （{@code
 * EXTERNAL_FINANCE}）是不够的：来源只能声明，留痕必须可查。两者共同构成解除条件，缺一即拒绝。
 *
 * <p><b>为什么留痕要绑定到「本次冻结」而不是「该主体曾有留痕」</b>（2026-11-16 独立审查 I-4 修复）：付款冻结会反复发生。
 * 若端口只回答「这个主体历史上有没有复核记录」，一次复核会永久放行此后所有冻结解除 —— 第二次、第三次冻结都不再需要复核，而 spec 要求的是
 * <b>每一次</b>解除都有留痕。因此入参带上本次冻结的发起时间 {@code blockedAt}，实现方据此只认「该时刻之后」的复核记录。
 *
 * <p>本切片未接财务复核系统，main 里没有实现；测试夹具可注入「已留痕 / 未留痕」两种取值，验证未留痕时必红。 生产实现属 R-2。
 */
public interface PaymentBlockReleaseReviewPort {

  /**
   * 该主体的<b>本次</b>付款冻结解除是否存在人工复核留痕。
   *
   * @param blockedAt 本次付款冻结的发起时间（取自 Vendor 迁移历史中最近一次 {@code payment_block_raised}）；为 null
   *     表示无法定位本次冻结，调用方须 fail-closed
   */
  boolean releaseReviewTraced(long tenantId, String partyId, Instant blockedAt);
}
