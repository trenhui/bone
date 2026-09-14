package com.bone.architecture.fixture.context.mine.application.command.handler;

import com.bone.architecture.fixture.context.shared.domain.SharedValue;
import com.bone.core.annotation.ReadSideOnly;

/** 规则单测夹具：application 命令处理器引用 @ReadSideOnly DSL（violation）。 */
public class PaymentCommandHandler {

  public String transform(SharedValue shared) {
    QueryDsl dsl = new QueryDsl();
    return dsl.query() + shared.value();
  }

  @ReadSideOnly
  static class QueryDsl {
    public String query() {
      return "rows";
    }
  }
}
