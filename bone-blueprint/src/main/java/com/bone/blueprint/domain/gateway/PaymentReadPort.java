package com.bone.blueprint.domain.gateway;

import com.bone.blueprint.domain.payment.read.PaymentRow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 支付读侧端口（§18.5 *ReadPort）。
 *
 * <p>读模型直查，不经过写聚合；同时承载超时支付单扫描（供超时关闭定时任务使用），避免写仓储堆砌 多条件查询方法（违反仓储方法白名单）。
 */
public interface PaymentReadPort {

  /** 按租户 + 支付单号查询单个支付读模型。 */
  Optional<PaymentRow> findById(long tenantId, long paymentId);

  /**
   * 查询指定时间之前仍处于待支付/支付中状态的支付单（供超时关闭扫描）。
   *
   * @param tenantId 租户
   * @param before 时间边界（createdAt 早于该时间视为超时）
   */
  List<PaymentRow> findPayableExpiredBefore(long tenantId, Instant before);
}
