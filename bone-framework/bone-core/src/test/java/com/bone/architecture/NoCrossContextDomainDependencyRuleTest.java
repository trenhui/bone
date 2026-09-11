package com.bone.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.architecture.fixture.context.mine.domain.CleanAggregate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;

/** P-10.4（D9）：跨上下文 domain 越界依赖拦截（M2 2.2）的单元测试。 */
class NoCrossContextDomainDependencyRuleTest {

  private static final String SELF = "com.bone.architecture.fixture.context.mine";
  private static final String SHARED = "com.bone.architecture.fixture.context.shared";

  @Test
  void crossContextDomainDependencyIsRejected() {
    ArchRule rule = BoneDddArchRules.noCrossContextDomainDependency(SELF, SHARED);
    JavaClasses all =
        new ClassFileImporter().importPackages("com.bone.architecture.fixture.context");
    EvaluationResult result = rule.evaluate(all);
    assertTrue(result.hasViolation(), () -> result.getFailureReport().toString());
    String report = result.getFailureReport().toString();
    // 越界目标被命中
    assertTrue(report.contains("OtherAggregate"), report);
    // 共享内核不误报
    assertFalse(report.contains("SharedValue"), report);
  }

  @Test
  void sharedKernelOnlyIsAllowed() {
    ArchRule rule = BoneDddArchRules.noCrossContextDomainDependency(SELF, SHARED);
    // 只导入合法夹具（及其依赖的 SharedValue），避免夹带违规夹具 MineAggregate
    JavaClasses clean = new ClassFileImporter().importClasses(CleanAggregate.class);
    EvaluationResult result = rule.evaluate(clean);
    assertFalse(result.hasViolation(), () -> result.getFailureReport().toString());
  }

  @Test
  void emptyMatchIsTreatedAsConfigurationError() {
    ArchRule rule =
        BoneDddArchRules.noCrossContextDomainDependency(
            "com.bone.architecture.fixture.context.nonexistent");
    JavaClasses none =
        new ClassFileImporter().importPackages("com.bone.architecture.fixture.context.nonexistent");
    // ArchUnit 1.2.1 对空匹配直接抛 AssertionError（failed to check any classes），即「配置错误」信号
    assertThrows(AssertionError.class, () -> rule.evaluate(none));
  }
}
