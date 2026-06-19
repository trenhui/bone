package com.bone.blueprint.domain.gateway;

public interface InventoryGateway {

  /** 校验库存是否充足（创建订单前置条件）。 */
  boolean checkStock(Long productId, Integer quantity);

  /** 创建订单后预留库存（最终一致：支付确认 / 取消释放）。 */
  void reserveStock(Long orderId, Long productId, Integer quantity);

  /** 支付成功后确认扣减（消费预留）。 */
  void confirmStock(Long orderId, Long productId, Integer quantity);

  /** 取消订单后释放预留。 */
  void releaseStock(Long orderId);
}
