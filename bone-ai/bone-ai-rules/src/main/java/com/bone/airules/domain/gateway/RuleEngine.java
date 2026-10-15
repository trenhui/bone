package com.bone.airules.domain.gateway;

import java.util.Map;

/**
 * 规则执行 SPI：治理面只定义契约，执行引擎由基础设施实现（QLExpress 唯一，总案 §4.3）。
 *
 * <p>{@code trace=true} 时返回表达式追踪路径（QLExpress isTrace），供判定路径回放写 trace 链路。
 */
public interface RuleEngine {

  /**
   * 执行一条规则表达式。
   *
   * @param expression QLExpress DSL 文本
   * @param context 求值上下文（字段值等，键为变量名）
   * @param trace 是否采集执行追踪路径
   * @return 求值结果（命中 / 追踪 / 耗时）
   */
  RuleEvaluation evaluate(String expression, Map<String, Object> context, boolean trace);

  /** 纯求值结果（不含规则元数据）；应用层组装为 {@code RuleEvalResult} 供回放与短路消费。 */
  record RuleEvaluation(boolean hit, String tracePath, long elapsedMs, String errorMessage) {

    public static RuleEvaluation of(boolean hit, String tracePath, long elapsedMs) {
      return new RuleEvaluation(hit, tracePath, elapsedMs, null);
    }

    public static RuleEvaluation error(String message, long elapsedMs) {
      return new RuleEvaluation(false, null, elapsedMs, message);
    }
  }
}
