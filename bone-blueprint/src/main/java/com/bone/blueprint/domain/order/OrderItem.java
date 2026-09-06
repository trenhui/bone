package com.bone.blueprint.domain.order;

import com.bone.blueprint.domain.shared.valueobject.Money;
import com.bone.core.annotation.Id;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import java.util.Date;
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
    Date now = new Date();
    this.setCreatedAt(now);
    this.setUpdatedAt(now);
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
   * 落库前回填真实订单 id。
   *
   * <p>构造期绑定的是会被持久化层覆盖的预分配订单 id；订单落库取回真实 id 后必须回填，否则明细 {@code order_id} 指向不存在的订单而成为孤儿行。
   */
  public void rebindOrderId(long persistedOrderId) {
    this.orderId = persistedOrderId;
  }

  public void updateQuantity(Integer newQuantity) {
    if (newQuantity == null || newQuantity <= 0) {
      throw new DomainException("商品数量必须大于0");
    }
    this.quantity = newQuantity;
    this.subtotal = getUnitPriceMoney().multiply(newQuantity).toBigDecimal();
  }
}
