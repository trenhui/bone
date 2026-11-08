package com.bone.claim.infrastructure.script;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.application.port.out.ScriptConditionPort;
import com.bone.claim.application.port.out.ScriptConditionPort.ConditionVerdict;
import com.bone.claim.application.port.out.ScriptConditionPort.RejectReason;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** M5b 受控求值：正向声明式条件 + 四类宿主能力越界负向探针。 */
class QlExpressScriptConditionAdapterTest {

  private final QlExpressScriptConditionAdapter adapter = new QlExpressScriptConditionAdapter();

  private static Map<String, Object> facts() {
    return Map.of("claimAmount", 12000, "evidenceParsed", true);
  }

  @Test
  @DisplayName("合法声明式条件返回可信布尔结果")
  void legalDeclarativeCondition() {
    ConditionVerdict pass = adapter.evaluate("claimAmount >= 10000", facts());
    assertThat(pass.trusted()).isTrue();
    assertThat(pass.passed()).isTrue();

    ConditionVerdict notPass = adapter.evaluate("claimAmount < 10000", facts());
    assertThat(notPass.trusted()).isTrue();
    assertThat(notPass.passed()).isFalse();
  }

  @Test
  @DisplayName("文件访问被标识符白名单阻断")
  void rejectsFileAccess() {
    assertRejected("new java.io.File(\"/etc/passwd\").exists()", RejectReason.FORBIDDEN_IDENTIFIER);
  }

  @Test
  @DisplayName("网络访问被标识符白名单阻断")
  void rejectsNetworkAccess() {
    assertRejected(
        "java.net.Socket(\"127.0.0.1\", 80).isConnected()", RejectReason.FORBIDDEN_IDENTIFIER);
  }

  @Test
  @DisplayName("反射调用被标识符白名单阻断")
  void rejectsReflection() {
    assertRejected(
        "claimAmount.getClass().forName(\"java.lang.Runtime\")", RejectReason.FORBIDDEN_IDENTIFIER);
  }

  @Test
  @DisplayName("类加载被标识符白名单阻断")
  void rejectsClassLoading() {
    assertRejected("Class.forName(\"java.lang.Runtime\")", RejectReason.FORBIDDEN_IDENTIFIER);
  }

  @Test
  @DisplayName("白名单之外的操作符在求值前拒绝（^ / ; / @）")
  void rejectsForbiddenOperators() {
    for (String expression :
        new String[] {"claimAmount ^ 1 > 0", "claimAmount > 1;", "claimAmount > 1 @ 2"}) {
      assertRejected(expression, RejectReason.FORBIDDEN_OPERATOR);
    }
  }

  @Test
  @DisplayName("操作符与内置函数白名单定义在端口上且不含宿主能力")
  void operatorWhitelistIsSingleSource() {
    assertThat(ScriptConditionPort.ALLOWED_OPERATORS)
        .contains("min", "max", "contains", "size")
        .doesNotContain("exec", "forName", "getClass");
    assertThat(ScriptConditionPort.ALLOWED_OPERATOR_CHARS)
        .contains('>', '&', '+')
        .doesNotContain('^', ';', '@');
  }

  @Test
  @DisplayName("非布尔结果不得被当成条件成立")
  void rejectsNonBooleanResult() {
    assertRejected("claimAmount + 1", RejectReason.NON_BOOLEAN_RESULT);
  }

  @Test
  @DisplayName("超长表达式与空表达式在求值前拒绝")
  void rejectsInvalidExpressions() {
    assertRejected("a".repeat(600), RejectReason.EXPRESSION_TOO_LONG);
    assertRejected(null, RejectReason.INVALID_EXPRESSION);
    assertRejected("   ", RejectReason.INVALID_EXPRESSION);
  }

  @Test
  @DisplayName("引擎求值异常按失败关闭返回可诊断原因")
  void evaluationErrorIsFailClosed() {
    ConditionVerdict verdict = adapter.evaluate("claimAmount >", facts());
    assertThat(verdict.failed()).isTrue();
    assertThat(verdict.rejectReason()).isIn(RejectReason.EVALUATION_ERROR, RejectReason.TIMEOUT);
    assertThat(verdict.diagnostic()).isNotNull();
  }

  private void assertRejected(String expression, RejectReason expected) {
    ConditionVerdict verdict = adapter.evaluate(expression, facts());
    assertThat(verdict.failed()).isTrue();
    assertThat(verdict.passed()).isFalse();
    assertThat(verdict.rejectReason()).isEqualTo(expected);
    assertThat(verdict.diagnostic()).isNotBlank();
  }
}
