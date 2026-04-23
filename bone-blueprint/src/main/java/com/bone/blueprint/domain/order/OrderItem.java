package com.bone.blueprint.domain.order;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 订单商品项
 * <p>
 * 表示订单中的单个商品，包含商品基本信息、数量、单价等
 * </p>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_order_item")
public class OrderItem extends AbstractEntity<Long> {

    private Long id;
    private Long orderId;
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;

    /**
     * 获取商品项ID
     * 
     * @return 商品项ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 获取订单ID
     * 
     * @return 订单ID
     */
    public Long getOrderId() {
        return orderId;
    }

    /**
     * 获取商品ID
     * 
     * @return 商品ID
     */
    public Long getProductId() {
        return productId;
    }

    /**
     * 获取商品名称
     * 
     * @return 商品名称
     */
    public String getProductName() {
        return productName;
    }

    /**
     * 获取商品数量
     * 
     * @return 商品数量
     */
    public Integer getQuantity() {
        return quantity;
    }

    /**
     * 获取商品单价
     * 
     * @return 商品单价
     */
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    /**
     * 获取商品小计
     * 
     * @return 商品小计
     */
    public BigDecimal getSubtotal() {
        return subtotal;
    }

    /**
     * 构造函数
     * 
     * @param id 商品项ID
     * @param orderId 订单ID
     * @param productId 商品ID
     * @param productName 商品名称
     * @param quantity 商品数量
     * @param unitPrice 商品单价
     */
    private OrderItem(Long id, Long orderId, Long productId, String productName,
                      Integer quantity, BigDecimal unitPrice) {
        this.id = id;
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * 创建订单商品项
     * 
     * @param id 商品项ID
     * @param orderId 订单ID
     * @param productId 商品ID
     * @param productName 商品名称
     * @param quantity 商品数量
     * @param unitPrice 商品单价
     * @return 订单商品项
     * @throws DomainException 当参数不合法时抛出
     */
    public static OrderItem create(Long id, Long orderId, Long productId, String productName,
                                   Integer quantity, BigDecimal unitPrice) {
        if (productId == null || productId <= 0) {
            throw new DomainException("商品ID无效");
        }
        if (quantity == null || quantity <= 0) {
            throw new DomainException("商品数量必须大于0");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("商品单价必须大于0");
        }
        return new OrderItem(id, orderId, productId, productName, quantity, unitPrice);
    }

    /**
     * 更新商品数量
     * 
     * @param newQuantity 新的商品数量
     * @throws DomainException 当数量不合法时抛出
     */
    public void updateQuantity(Integer newQuantity) {
        if (newQuantity == null || newQuantity <= 0) {
            throw new DomainException("商品数量必须大于0");
        }
        this.quantity = newQuantity;
        this.subtotal = this.unitPrice.multiply(BigDecimal.valueOf(newQuantity));
    }
}
