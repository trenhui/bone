package com.bone.blueprint.domain.model.payment.valueobject;

/** 支付单状态机：PENDING → PAYING → SUCCESS / FAILED / CLOSED。 */
public enum PaymentStatus {
  /** 已创建（订单发起支付后，尚未提交渠道） */
  PENDING,
  /** 支付中（已向支付渠道预下单，等待用户完成/回调） */
  PAYING,
  /** 支付成功（渠道回调确认，幂等） */
  SUCCESS,
  /** 支付失败（渠道明确失败或校验失败） */
  FAILED,
  /** 已关闭（超时未支付 / 订单取消） */
  CLOSED
}
