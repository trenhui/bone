package com.bone.architecture.fixture.application.command.handler;

import com.bone.architecture.fixture.infrastructure.query.ReadDslGateway;

/** 规则单测夹具：命令处理器引用 @ReadSideOnly 标记的 DSL（commandHandlersMustNotUseQueryBuilder 违规）。 */
public class PaymentCommandHandler {

  public String transform() {
    return new ReadDslGateway().rows();
  }
}
