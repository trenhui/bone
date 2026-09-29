package com.bone.blueprint.domain.model.order;

import com.bone.blueprint.domain.model.shared.valueobject.Money;
import com.bone.core.annotation.Id;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_order_item")
public class OrderItem extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private Long orderId;
  private Long productId;
  private String productName;
  private Integer quantity;
  private BigDecimal unitPrice;
  private BigDecimal subtotal;

  public Money getUnitPriceMoney() {
    return unitPrice == null ? Money.zero() : Money.of(unitPrice);
  }

  public Money getSubtotalMoney() {
    return subtotal == null ? Money.zero() : Money.of(subtotal);
  }

  private OrderItem(
      Long id,
      Long orderId,
      Long productId,
      String productName,
      Integer quantity,
      Money unitPrice) {
    this.id = id;
    this.orderId = orderId;
    this.productId = productId;
    this.productName = productName;
    this.quantity = quantity;
    this.unitPrice = unitPrice.toBigDecimal();
    this.subtotal = unitPrice.multiply(quantity).toBigDecimal();
    // created_at / updated_at 由 AbstractEntity 基类在 SDK save 时自动填充，
    // 这里无需手动设置（父类字段是 java.util.Date 类型，SDK 会用当前时间填充）。
  }

  public static OrderItem create(
      Long id,
      Long orderId,
      Long productId,
      String productName,
      Integer quantity,
      BigDecimal unitPrice) {
    if (productId == null || productId <= 0) {
      throw new DomainException("商品ID无效");
    }
    if (quantity == null || quantity <= 0) {
      throw new DomainException("商品数量必须大于0");
    }
    if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
      throw new DomainException("商品单价必须大于0");
    }
    Money price = Money.of(unitPrice);
    return new OrderItem(id, orderId, productId, productName, quantity, price);
  }

  /**
   * 更新明细数量（<b>包级可见</b>）。
   *
   * <p>聚合根 Order 通过 {@link Order#updateItemQuantity(long, int)} 作为唯一写入口调本方法， 确保修改后会 {@link
   * Order#recalculateTotal()} 重算总金额。对外（application / adapter 层）不可直接调， 避免绕过聚合根造成 Order.totalAmount
   * 与明细 subtotal 之和不一致。
   */
  void updateQuantity(Integer newQuantity) {
    if (newQuantity == null || newQuantity <= 0) {
      throw new DomainException("商品数量必须大于0");
    }
    this.quantity = newQuantity;
    this.subtotal = getUnitPriceMoney().multiply(newQuantity).toBigDecimal();
  }
}
