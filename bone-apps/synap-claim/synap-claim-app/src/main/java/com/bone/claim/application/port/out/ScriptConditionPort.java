package com.bone.claim.application.port.out;

import java.util.Map;
import java.util.Set;

/**
 * M5b 受控脚本条件求值端口（出站）。
 *
 * <p><b>不自造解释器</b>：实现必须复用项目唯一 DSL 引擎 QLExpress4 的安全隔离与超时能力，本层只做静态预检与结果收敛。
 *
 * <p><b>白名单单一真源</b>：{@link #ALLOWED_OPERATORS} 与 {@link #ALLOWED_OPERATOR_CHARS} 定义在端口上，
 * 适配器不得另维护一份——两处清单分叉时，静态预检会放行宿主能力而隔离策略未必拦得住。
 *
 * <p><b>fail-closed 语义</b>：{@code trusted=false} 表示求值不可信（越界、异常、超时、结果类型错），调用方必须停止推进， 不得把 {@code
 * passed=false} 当成「条件不成立」继续走业务路径——两者在安全上不等价。
 */
public interface ScriptConditionPort {

  /** 允许的内置函数：纯算术 / 集合判定，不含任何 IO、反射、类加载能力。 */
  Set<String> ALLOWED_OPERATORS =
      Set.of("min", "max", "abs", "round", "contains", "size", "isEmpty");

  /** 允许的运算符字符：比较、逻辑、四则、取模、三元；其余字符在求值前一律拒绝。 */
  Set<Character> ALLOWED_OPERATOR_CHARS =
      Set.of('>', '<', '=', '!', '&', '|', '+', '-', '*', '/', '%', '?', ':');

  ConditionVerdict evaluate(String expression, Map<String, Object> facts);

  /** 拒绝原因码（可诊断、可回放，不含表达式原文中的敏感值）。 */
  enum RejectReason {
    INVALID_EXPRESSION,
    EXPRESSION_TOO_LONG,
    FORBIDDEN_IDENTIFIER,
    FORBIDDEN_OPERATOR,
    NON_BOOLEAN_RESULT,
    EVALUATION_ERROR,
    TIMEOUT,
    ENGINE_UNAVAILABLE
  }

  /** {@code trusted=false} 时 {@code passed} 无意义，调用方一律按失败关闭处理。 */
  record ConditionVerdict(
      boolean passed, boolean trusted, RejectReason rejectReason, String diagnostic) {

    public static ConditionVerdict pass(boolean value) {
      return new ConditionVerdict(value, true, null, null);
    }

    public static ConditionVerdict reject(RejectReason reason, String diagnostic) {
      return new ConditionVerdict(false, false, reason, diagnostic);
    }

    public boolean failed() {
      return !trusted;
    }
  }
}
