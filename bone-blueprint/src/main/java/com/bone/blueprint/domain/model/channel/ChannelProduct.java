package com.bone.blueprint.domain.model.channel;

import com.bone.blueprint.domain.model.channel.valueobject.ListingStatus;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 渠道商品聚合根 —— 「一个内部商品 × 一个渠道」的上架关系。
 *
 * <p><b>为何不是「商品」本身</b>：商品主数据在 bone-masterdata（{@code MD_PRODUCT}），本模块只消费不拥有。
 * 本表承载的是<strong>商品在特定渠道的上架状态</strong>——同一件商品在淘宝在售、在京东下架是常态， 因此主键是 {@code (tenant_id, channel_code,
 * product_id)} 三元组，而不是 product_id。
 *
 * <p><b>为何要冗余 productName / listingPrice 快照</b>：渠道侧商品的标题与价格一旦提交即独立演化
 * （运营会在渠道后台改价）。落快照才能在「内部主数据改价了但渠道没同步」时做出对账差异； 只存外键的话，历史对账无从下手。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("bp_channel_product")
public class ChannelProduct extends TenantAggregateRoot<Long> {

  private String channelCode;
  private Long productId;
  private String productName;

  /** 渠道侧商品 ID：上架成功后由扩展实现回填。 */
  private String channelProductId;

  private ListingStatus listingStatus;
  private BigDecimal listingPrice;
  private Integer listingStock;
  private Instant lastSyncAt;
  private String failReason;
  private Instant createdAt;
  private Instant updatedAt;

  @Version private Long version;

  private ChannelProduct(
      long id,
      Long tenantId,
      String channelCode,
      Long productId,
      String productName,
      BigDecimal listingPrice) {
    setId(id);
    setTenantId(tenantId);
    this.channelCode = channelCode;
    this.productId = productId;
    this.productName = productName;
    this.listingPrice = listingPrice;
    this.listingStatus = ListingStatus.UNLISTED;
    this.listingStock = 0;
    touch();
  }

  public static ChannelProduct create(
      long id,
      Long tenantId,
      String channelCode,
      Long productId,
      String productName,
      BigDecimal listingPrice) {
    return new ChannelProduct(id, tenantId, channelCode, productId, productName, listingPrice);
  }

  /** 置为「上架中」：向渠道提交前的状态迁移，重复提交会被 {@link #canList} 拒绝。 */
  public void markListing(BigDecimal price) {

    touch();
    if (!listingStatus.canList()) {
      throw new IllegalStateException(
          "当前状态不允许上架: "
              + listingStatus
              + "（channel="
              + channelCode
              + ", product="
              + productId
              + "）");
    }
    if (price != null) {
      this.listingPrice = price;
    }
    this.listingStatus = ListingStatus.LISTING;
    this.failReason = null;
  }

  /** 渠道确认上架成功，回填渠道商品 ID。 */
  public void markOnline(String channelProductId, Instant at) {

    touch();
    this.listingStatus = ListingStatus.ONLINE;
    this.channelProductId = channelProductId;
    this.lastSyncAt = at;
    this.failReason = null;
  }

  /** 上架失败：记录渠道返回的原因，回到可重试的 FAILED 态。 */
  public void markFailed(String reason, Instant at) {

    touch();
    this.listingStatus = ListingStatus.FAILED;
    this.failReason = reason == null ? "渠道未返回原因" : reason;
    this.lastSyncAt = at;
  }

  /** 置为「下架中」。 */
  public void markDelisting() {

    touch();
    if (!listingStatus.canDelist()) {
      throw new IllegalStateException(
          "当前状态不允许下架: "
              + listingStatus
              + "（channel="
              + channelCode
              + ", product="
              + productId
              + "）");
    }
    this.listingStatus = ListingStatus.DELISTING;
  }

  /** 渠道确认下架成功：清空渠道商品 ID（该 ID 已失效，留着会让后续误以为仍在线）。 */
  public void markOffline(Instant at) {

    touch();
    this.listingStatus = ListingStatus.OFFLINE;
    this.channelProductId = null;
    this.lastSyncAt = at;
  }

  /** 记录一次库存同步结果（对账用）。 */
  public void markStockSynced(int stock, Instant at) {

    touch();
    this.listingStock = stock;
    this.lastSyncAt = at;
  }

  /** 更新商品名称（重复上架时以最新主数据为准）。 */
  public void updateProductName(String productName) {
    if (productName != null && !productName.isBlank()) {
      this.productName = productName;
    }
  }

  public boolean canList() {
    return listingStatus.canList();
  }

  public boolean canDelist() {
    return listingStatus.canDelist();
  }

  /** 刷新修改时间；创建时间只在构造时赋值一次，之后不再变动。 */
  private void touch() {
    this.updatedAt = Instant.now();
    if (this.createdAt == null) {
      this.createdAt = this.updatedAt;
    }
  }
}
