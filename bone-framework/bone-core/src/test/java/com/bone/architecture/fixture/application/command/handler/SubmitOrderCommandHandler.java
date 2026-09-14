package com.bone.architecture.fixture.application.command.handler;

import org.springframework.transaction.annotation.Transactional;

/** 规则单测夹具：合法的命令处理器名 + 类级 @Transactional。 */
@Transactional
public class SubmitOrderCommandHandler {

  public String handle(String command) {
    return "ok";
  }
}
