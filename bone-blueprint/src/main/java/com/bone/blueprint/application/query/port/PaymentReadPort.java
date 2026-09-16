package com.bone.blueprint.application.query.port;

import com.bone.blueprint.application.query.dto.PaymentRow;
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

  /**
   * 查询指定时间之前仍处于待支付/支付中状态的支付单（<b>全租户</b>，供运维型定时任务扫描）。
   *
   * <p>理由同 {@link OrderReadPort#findCreatedExpiredBeforeAllTenants}：定时线程无请求上下文，
   * 按"当前租户"扫描会导致除平台租户外的超时支付单永不关闭。
   */
  List<PaymentRow> findPayableExpiredBeforeAllTenants(Instant before);

  /**
   * 查询指定时间之前已 SUCCESS 的支付单（<b>全租户</b>，供"钱货不一致"对账扫描）。
   *
   * <p>对账用途：支付单已成功但订单仍停在 CREATED，说明支付成功事件的下游链路（订单确认）未执行成功， 必须留痕告警——只打 info 日志会让这类资金异常永久沉没。
   */
  List<PaymentRow> findSuccessCreatedBeforeAllTenants(Instant before);
}
