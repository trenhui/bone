package com.bone.blueprint.domain.model.channel.valueobject;

/**
 * 渠道商品上架状态机。
 *
 * <p>状态流转：{@code UNLISTED → LISTING → ONLINE → DELISTING → OFFLINE}，旁路 {@code FAILED}。
 *
 * <p><b>为何 LISTING / DELISTING 是独立中间态</b>：渠道开放平台的上架/下架是<strong>异步</strong>的 （提交后需渠道侧审核或轮询结果），若只有
 * ONLINE/OFFLINE 两态，则「已提交但渠道未确认」这一段时间 无法表达——这段窗口里重复提交会造成渠道侧重复建品。中间态让重复提交可被幂等拒绝。
 */
public enum ListingStatus {
  /** 未上架（初始态）。 */
  UNLISTED,
  /** 上架中：已提交渠道，等待渠道确认。 */
  LISTING,
  /** 已上架：渠道侧已有商品且可售卖。 */
  ONLINE,
  /** 下架中：已提交下架，等待渠道确认。 */
  DELISTING,
  /** 已下架。 */
  OFFLINE,
  /** 上架失败：渠道拒绝（如类目不符、资质缺失），{@code failReason} 必填。 */
  FAILED;

  /** 是否处于「可继续向渠道发起上架」的态。 */
  public boolean canList() {
    return this == UNLISTED || this == OFFLINE || this == FAILED;
  }

  /** 是否处于「可继续向渠道发起下架」的态。 */
  public boolean canDelist() {
    return this == ONLINE;
  }

  /** 是否处于终态（渠道已确认，不再变化）。 */
  public boolean isTerminal() {
    return this == ONLINE || this == OFFLINE || this == FAILED;
  }
}
