package com.bone.blueprint.domain.model.inventory;

import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 库存聚合根 —— 「一个商品 × 一个仓库」的可用量。
 *
 * <p><b>本实体的存在意义</b>：原本交易域的库存校验走 {@code InventoryGateway} 的 Mock 实现 （{@code
 * MockInventoryGatewayAdapter#checkStock} 恒返回 true），那是<strong>占位实现</strong>，
 * 超卖在联调环境永远测不出来。真实的多渠道场景下库存是共享的——淘宝卖掉一件，抖音的可用量必须同步减少， 否则四个渠道各自按自己的数卖，必然超卖。因此库存必须落在交易域内部，成为事实真源。
 *
 * <p><b>并发模型</b>：预留（reserve）/确认（confirm）/释放（release）全部走 {@code @Version} 乐观锁， 不使用 {@code SELECT ...
 * FOR UPDATE}（架构硬约束：悲观锁易死锁，且多副本部署下行锁完全失效）。 高冲突场景由 {@code InventoryReservationService} 的重试 + 唯一约束兜底。
 *
 * <p><b>不变量</b>：{@code availableQty >= 0} 恒成立。预留时若不足则抛异常，不允许负库存
 * ——负库存会把「超卖」这个业务问题降级成数据问题，事后无法区分是超卖还是记账错误。
 *
 * <p><b>事件豁免（E-5.4）</b>：本聚合的库存增减（{@code receive/deduct/reserve/confirm/release}）为<b>内部
 * 状态迁移</b>，写路径不配 {@code publishFrom}、不发 DomainEvent；跨聚合协作（如渠道拉单防超卖）走 应用层服务编排 + AFTER_COMMIT
 * 事件，而非库存自身发事件。若未来库存调整需通知下游订阅方，应补发 {@code InventoryAdjustedEvent} 并登记。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("bp_inventory")
public class Inventory extends TenantAggregateRoot<Long> {

  private Long productId;
  private String productName;
  private String warehouseCode;
  private Integer availableQty;
  private Integer reservedQty;
  private Integer safetyStock;
  private Instant createdAt;
  private Instant updatedAt;

  @Version private Long version;

  private Inventory(
      long id,
      Long tenantId,
      Long productId,
      String productName,
      String warehouseCode,
      int initialQty,
      int safetyStock) {
    setId(id);
    setTenantId(tenantId);
    this.productId = productId;
    this.productName = productName;
    this.warehouseCode =
        warehouseCode == null || warehouseCode.isBlank() ? "DEFAULT" : warehouseCode;
    this.availableQty = Math.max(0, initialQty);
    this.reservedQty = 0;
    this.safetyStock = Math.max(0, safetyStock);
    touch();
  }

  public static Inventory create(
      long id,
      Long tenantId,
      Long productId,
      String productName,
      String warehouseCode,
      int initialQty,
      int safetyStock) {
    return new Inventory(
        id, tenantId, productId, productName, warehouseCode, initialQty, safetyStock);
  }

  /** 入库（采购到货 / 手动调整），{@code delta} 必须为正。 */
  public void receive(int delta) {

    touch();
    if (delta <= 0) {
      throw new IllegalArgumentException("入库数量必须为正: " + delta);
    }
    this.availableQty += delta;
  }

  /** 扣减（报损 / 纠错），不允许扣成负数。 */
  public void deduct(int delta) {

    touch();
    if (delta <= 0) {
      throw new IllegalArgumentException("扣减数量必须为正: " + delta);
    }
    if (this.availableQty < delta) {
      throw new DomainException(
          "可用库存不足，无法扣减: product=" + productId + ", available=" + availableQty + ", delta=" + delta);
    }
    this.availableQty -= delta;
  }

  /**
   * 预留：下单时占用库存（available 减、reserved 增）。
   *
   * @throws DomainException 可用量不足——这是超卖的唯一拦截点，调用方必须把该异常转成业务错误码
   */
  public void reserve(int quantity) {

    touch();
    if (quantity <= 0) {
      throw new IllegalArgumentException("预留数量必须为正: " + quantity);
    }
    if (this.availableQty < quantity) {
      throw new DomainException(
          "库存不足: product=" + productId + ", available=" + availableQty + ", required=" + quantity);
    }
    this.availableQty -= quantity;
    this.reservedQty += quantity;
  }

  /** 确认出库：预留量真正出库（reserved 减）。 */
  public void confirm(int quantity) {

    touch();
    if (quantity <= 0) {
      throw new IllegalArgumentException("确认数量必须为正: " + quantity);
    }
    if (this.reservedQty < quantity) {
      throw new DomainException(
          "预留量不足，无法确认出库: product="
              + productId
              + ", reserved="
              + reservedQty
              + ", required="
              + quantity);
    }
    this.reservedQty -= quantity;
  }

  /** 释放预留：取消订单 / 超时关单时把占用还回可用池。 */
  public void release(int quantity) {

    touch();
    if (quantity <= 0) {
      throw new IllegalArgumentException("释放数量必须为正: " + quantity);
    }
    int actual = Math.min(quantity, this.reservedQty);
    this.reservedQty -= actual;
    this.availableQty += actual;
  }

  /** 是否低于安全库存（补货预警判据）。 */
  public boolean isBelowSafetyStock() {
    return this.availableQty <= this.safetyStock;
  }

  public void updateSafetyStock(int safetyStock) {

    touch();
    this.safetyStock = Math.max(0, safetyStock);
  }

  public void updateProductName(String productName) {

    touch();
    this.productName = productName;
  }

  /** 刷新修改时间；创建时间只在构造时赋值一次，之后不再变动。 */
  private void touch() {
    this.updatedAt = Instant.now();
    if (this.createdAt == null) {
      this.createdAt = this.updatedAt;
    }
  }
}
