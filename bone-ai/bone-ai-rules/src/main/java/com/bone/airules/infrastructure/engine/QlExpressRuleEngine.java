package com.bone.airules.infrastructure.engine;

import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.InitOptions;
import com.alibaba.qlexpress4.QLOptions;
import com.alibaba.qlexpress4.QLResult;
import com.alibaba.qlexpress4.exception.QLException;
import com.alibaba.qlexpress4.runtime.trace.ExpressionTrace;
import com.bone.airules.domain.gateway.RuleEngine;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QLExpress 规则引擎实现（总案 §4.3 唯一 DSL，qlexpress4 4.1.3）。
 *
 * <p>要点：
 *
 * <ul>
 *   <li>指令集缓存（{@code QLOptions.cache(true)}），规则热更走版本链，不重建 runner；
 *   <li>{@code traceExpression(true)} 采集表达式追踪树——「判定路径可回放」的采集点；
 *   <li>执行超时（{@code timeoutMillis}）为沙箱防线之一：脚本超时强制失败按未命中处理；
 *   <li>保持默认安全策略：不开放任意类 / 函数调用，禁止脚本与应用代码交互；
 *   <li>求值异常按未命中处理（与 SpEL 求值器一致：异常回 false），错误信息保留供回放。
 * </ul>
 */
public class QlExpressRuleEngine implements RuleEngine {

  private static final Logger log = LoggerFactory.getLogger(QlExpressRuleEngine.class);

  /** 规则执行超时（毫秒）：声明式规则为常量级求值，1s 上限防恶意/失控脚本。 */
  private static final long EVAL_TIMEOUT_MS = 1000;

  private final Express4Runner runner;

  public QlExpressRuleEngine() {
    // InitOptions.DEFAULT_OPTIONS：默认安全策略（不开放任意类 / 函数调用）
    this.runner = new Express4Runner(InitOptions.DEFAULT_OPTIONS);
    log.info("QlExpressRuleEngine initialized (qlexpress4 unique DSL engine)");
  }

  @Override
  public RuleEvaluation evaluate(String expression, Map<String, Object> context, boolean trace) {
    long start = System.nanoTime();
    QLOptions options =
        QLOptions.builder()
            .cache(true)
            .traceExpression(trace)
            .timeoutMillis(EVAL_TIMEOUT_MS)
            .build();
    QLResult result;
    try {
      result = runner.execute(expression, context == null ? Map.of() : context, options);
    } catch (QLException e) {
      log.warn("Rule evaluation failed (QLException), treat as miss. expr={}", expression, e);
      String reason = e.getReason() != null ? e.getReason() : e.getMessage();
      return RuleEvaluation.error(reason, elapsedMs(start));
    } catch (Exception e) {
      log.warn("Rule evaluation failed, treat as miss. expr={}", expression, e);
      return RuleEvaluation.error(e.getMessage(), elapsedMs(start));
    }
    String tracePath = trace ? renderTrace(result.getExpressionTraces()) : null;
    return RuleEvaluation.of(toBoolean(result.getResult()), tracePath, elapsedMs(start));
  }

  /** 追踪树 → 可读路径（回放 / 审计用）。 */
  private String renderTrace(List<ExpressionTrace> traces) {
    if (traces == null || traces.isEmpty()) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    for (ExpressionTrace t : traces) {
      if (t != null) {
        sb.append(t.toPrettyString(0)).append('\n');
      }
    }
    String s = sb.toString().trim();
    return s.isEmpty() ? null : s;
  }

  private boolean toBoolean(Object result) {
    if (result == null) {
      return false;
    }
    if (result instanceof Boolean b) {
      return b;
    }
    if (result instanceof Number n) {
      return n.doubleValue() != 0;
    }
    if (result instanceof String s) {
      return !s.isEmpty() && !"false".equalsIgnoreCase(s);
    }
    return true;
  }

  private long elapsedMs(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000;
  }
}
