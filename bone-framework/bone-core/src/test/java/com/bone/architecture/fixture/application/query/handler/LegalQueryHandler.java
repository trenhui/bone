package com.bone.architecture.fixture.application.query.handler;

import org.springframework.transaction.annotation.Transactional;

/** 规则单测夹具：合规 QueryHandler——类级 readOnly 事务、方法名 handle 落在事务入口判定内。 */
@Transactional(readOnly = true)
public class LegalQueryHandler {

  public String handle() {
    return "ok";
  }
}
