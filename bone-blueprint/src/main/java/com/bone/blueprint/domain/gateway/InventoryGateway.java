package com.bone.blueprint.domain.gateway;

/**
 * 库存 ACL 出站端口（防腐层接口，E-10）：实现在 infrastructure/gateway。
 *
 * <p><b>粒度契约（契约 Artifact，2026-10-06 澄清）</b>：预留（{@link #reserveStock}）/ 扣减（{@link #confirmStock}）/
 * 释放（{@link #releaseStockLine}）三条链路统一为 <strong>行粒度</strong>（订单 + 明细行）， 事件订阅侧共用 {@code
 * OrderItemInventoryExecutor} 逐行驱动。三者同构后，任何一侧失败都留有
 * 明细级留痕（productId/quantity），补偿对账可精确到行；「某单侧整单到底做了什么」在真实网关实现里 是可回答的。
 *
 * <p><b>为何不再暴露整单释放（历史教训）</b>：旧签名 {@code releaseStock(orderId)} 把「释放哪些行、 放多少」留给实现者猜——按「剩余预留行」释放则已被
 * {@code confirmStock} 消费的预留无处安放， 按「整单数量」后退则凭空加库存（静默超卖/少卖）。歧义留在接口上等于坑留给下一个
 * 接入者，故删除；已确认预留的整单级补偿（极少见）由真实实现按订单查询预留明细后逐行走 {@link #releaseStockLine}， 不在 ACL 端口上留第二个签名。
 */
public interface InventoryGateway {

  /** 校验库存是否充足（创建订单前置条件）。 */
  boolean checkStock(Long productId, Integer quantity);

  /** 创建订单后按明细行预留库存（最终一致：支付确认扣减 / 取消释放，均非阻塞）。 */
  void reserveStock(Long orderId, Long productId, Integer quantity);

  /** 支付成功后按明细行确认扣减（消费预留）。 */
  void confirmStock(Long orderId, Long productId, Integer quantity);

  /**
   * 取消/退款后按明细行释放预留。
   *
   * <p><b>前置语义</b>：仅对「已预留、尚未确认扣减」的行有意义；订单已支付（预留已被 {@link #confirmStock}
   * 消费）的场景调用方<strong>不得</strong>调用——走了货的库存不能因取消/退款凭空 回加，退款退货走独立退货入库链路（不在本端口）。
   *
   * @param orderId 订单 ID
   * @param productId 明细行商品 ID
   * @param quantity 明细行购买数量
   */
  void releaseStockLine(Long orderId, Long productId, Integer quantity);
}
