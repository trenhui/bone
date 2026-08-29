package com.bone.blueprint.application.command.cmd;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 关闭超时未支付支付单命令（由定时任务扫描后逐笔下发）。 */
@Getter
@RequiredArgsConstructor
public class CloseExpiredPaymentCommand {

  private final Long paymentId;
}
