package com.bone.claim.infrastructure.script;

import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.InitOptions;
import com.alibaba.qlexpress4.QLOptions;
import com.alibaba.qlexpress4.QLResult;
import com.alibaba.qlexpress4.exception.QLException;
import com.alibaba.qlexpress4.security.QLSecurityStrategy;
import com.bone.claim.application.port.out.ScriptConditionPort;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * M5b 脚本条件适配器：复用项目唯一 DSL 引擎 QLExpress4（4.1.3），不自造表达式解释器。
 *
 * <p>四层防线，与总体技术方案「把禁 IO / 超时 / 资源上限交给成熟库」一致：
 *
 * <ol>
 *   <li><b>库级隔离</b>：{@code QLSecurityStrategy.isolation()} 禁 {@code import} / {@code new} / 反射 /
 *       外部类静态调用，阻断规则表达式 RCE 面。
 *   <li><b>资源上限</b>：{@code QLOptions.timeoutMillis(1000)} 由引擎强制中断失控脚本；表达式长度与运算符
 *       数量上限在求值前静态拦截，避免把复杂表达式交给引擎后再超时。
 *   <li><b>标识符白名单</b>：表达式中出现的标识符只允许事实上下文的 key 与显式内置函数名，任一宿主 API 标识符（文件 / 网络 / 反射 / 类加载）直接拒绝。
 *   <li><b>结果收敛</b>：只采信 {@code Boolean} 结果；异常、非布尔值一律 {@code trusted=false}。
 * </ol>
 *
 * <p>本类不持有任何仓储、不发布领域事件——求值失败不写业务状态。
 */
@Component
public final class QlExpressScriptConditionAdapter implements ScriptConditionPort {

  private static final Logger log = LoggerFactory.getLogger(QlExpressScriptConditionAdapter.class);

  /** 表达式长度上限：声明式条件不需要长脚本，超长即疑似注入。 */
  static final int MAX_EXPRESSION_LENGTH = 512;

  /** 运算符数量上限：静态近似执行步数上限，与引擎超时互为独立防线。 */
  static final int MAX_OPERATORS = 64;

  /** 引擎级执行超时（毫秒），与 {@code QlExpressRuleEngine} 同口径。 */
  static final long TIMEOUT_MS = 1000;

  /** 字面量 + 端口白名单内置函数；不在适配器里另写一份函数清单。 */
  private static final Set<String> ALLOWED_BUILTINS = buildAllowedBuiltins();

  private static Set<String> buildAllowedBuiltins() {
    Set<String> allowed = new HashSet<>(Set.of("true", "false", "null", "and", "or", "not"));
    allowed.addAll(ScriptConditionPort.ALLOWED_OPERATORS);
    return Set.copyOf(allowed);
  }

  /** 宿主能力标识符黑名单（小写比较）：文件 / 网络 / 反射 / 类加载 / 进程 / 线程。 */
  private static final Set<String> FORBIDDEN_TOKENS =
      Set.of(
          "import",
          "new",
          "class",
          "getclass",
          "forname",
          "runtime",
          "system",
          "process",
          "file",
          "socket",
          "url",
          "classloader",
          "exec",
          "reflect",
          "thread",
          "securitymanager",
          "processbuilder",
          "method",
          "field",
          "constructor",
          "invoke");

  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  private static final Pattern OPERATOR = Pattern.compile("[+\\-*/%<>=!&|?:]");

  /**
   * 候选操作符：排除标识符字符、空白、字符串引号、括号、点号与逗号后剩下的字符。
   *
   * <p>用「排除法」而不是枚举已知危险字符，是因为后者永远追不上新语法：只要不在 {@link ScriptConditionPort#ALLOWED_OPERATOR_CHARS}
   * 白名单里，一律先拒绝。
   */
  private static final Pattern NON_TOKEN =
      Pattern.compile("[^\\w\\s\"'().,]", Pattern.UNICODE_CHARACTER_CLASS);

  private final Express4Runner runner;

  public QlExpressScriptConditionAdapter() {
    this.runner =
        new Express4Runner(
            InitOptions.builder().securityStrategy(QLSecurityStrategy.isolation()).build());
    log.info(
        "M5b script condition evaluator ready (QLExpress4 isolation + {}ms timeout)", TIMEOUT_MS);
  }

  @Override
  public ConditionVerdict evaluate(String expression, Map<String, Object> facts) {
    if (expression == null || expression.isBlank()) {
      return ConditionVerdict.reject(RejectReason.INVALID_EXPRESSION, "表达式为空");
    }
    if (expression.length() > MAX_EXPRESSION_LENGTH) {
      return ConditionVerdict.reject(RejectReason.EXPRESSION_TOO_LONG, "表达式超过长度上限");
    }
    if (countMatches(OPERATOR, expression) > MAX_OPERATORS) {
      return ConditionVerdict.reject(RejectReason.EXPRESSION_TOO_LONG, "表达式运算符数量超过上限");
    }

    Matcher operators = NON_TOKEN.matcher(expression);
    while (operators.find()) {
      char candidate = operators.group().charAt(0);
      if (!ScriptConditionPort.ALLOWED_OPERATOR_CHARS.contains(candidate)) {
        log.warn("M5b condition rejected: forbidden operator={}", candidate);
        return ConditionVerdict.reject(RejectReason.FORBIDDEN_OPERATOR, "不允许的操作符: " + candidate);
      }
    }

    Map<String, Object> safeFacts = facts == null ? Map.of() : Map.copyOf(facts);
    Set<String> allowed = new HashSet<>(ALLOWED_BUILTINS);
    allowed.addAll(safeFacts.keySet());

    Matcher identifiers = IDENTIFIER.matcher(expression);
    while (identifiers.find()) {
      String id = identifiers.group();
      String lower = id.toLowerCase(Locale.ROOT);
      if (FORBIDDEN_TOKENS.contains(lower) || !allowed.contains(id)) {
        log.warn("M5b condition rejected: forbidden identifier={}", id);
        return ConditionVerdict.reject(RejectReason.FORBIDDEN_IDENTIFIER, "不允许多标识符: " + id);
      }
    }

    QLOptions options = QLOptions.builder().cache(true).timeoutMillis(TIMEOUT_MS).build();
    try {
      QLResult result = runner.execute(expression, safeFacts, options);
      Object value = result.getResult();
      if (!(value instanceof Boolean booleanValue)) {
        return ConditionVerdict.reject(RejectReason.NON_BOOLEAN_RESULT, "条件结果不是布尔值");
      }
      return ConditionVerdict.pass(booleanValue);
    } catch (QLException e) {
      String diagnostic = e.getReason() != null ? e.getReason() : e.getMessage();
      boolean timeout = e.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("timeout");
      log.warn("M5b condition evaluation failed (timeout={})", timeout, e);
      return ConditionVerdict.reject(
          timeout ? RejectReason.TIMEOUT : RejectReason.EVALUATION_ERROR, diagnostic);
    } catch (Exception e) {
      log.warn("M5b condition evaluation failed", e);
      return ConditionVerdict.reject(RejectReason.EVALUATION_ERROR, e.getMessage());
    }
  }

  private static int countMatches(Pattern pattern, String text) {
    Matcher matcher = pattern.matcher(text);
    int count = 0;
    while (matcher.find()) {
      count++;
    }
    return count;
  }
}
