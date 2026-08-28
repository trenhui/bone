package com.bone.blueprint.application.command.cmd;

import lombok.Builder;
import lombok.Getter;

/** 订单发起支付命令：加载订单生成支付单并提交支付渠道。 */
@Getter
@Builder
public class InitiatePaymentCommand {

  /** 待支付订单 ID。 */
  private final Long orderId;
}
