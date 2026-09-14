package com.bone.architecture.fixture.application.command.handler;

/** 规则单测夹具：命名不合规（缺 CommandHandler 后缀）且无 @Transactional。 */
public class OrderCommandProcessor {

  public String handle(String command) {
    return "ok";
  }
}
