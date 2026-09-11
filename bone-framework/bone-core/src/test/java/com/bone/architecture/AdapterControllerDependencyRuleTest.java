package com.bone.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.architecture.fixture.adapter.controller.LegalController;
import com.bone.architecture.fixture.adapter.controller.OrderController;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;

/** E-6：Controller 依赖门面 / 编排层合法、直注服务实现与 Manager 违规（M2 2.6）的单元测试。 */
class AdapterControllerDependencyRuleTest {

  private static final ArchRule RULE =
      BoneDddArchRules.adapterControllersMustNotDependOnApplicationService();

  @Test
  void facadeAndOrchestrationOnlyAreAllowed() {
    JavaClasses legal = new ClassFileImporter().importClasses(LegalController.class);
    EvaluationResult result = RULE.evaluate(legal);
    assertFalse(result.hasViolation(), () -> result.getFailureReport().toString());
  }

  @Test
  void serviceImplAndManagerAreRejected() {
    JavaClasses illegal = new ClassFileImporter().importClasses(OrderController.class);
    EvaluationResult result = RULE.evaluate(illegal);
    assertTrue(result.hasViolation());
    String report = result.getFailureReport().toString();
    assertTrue(report.contains("OrderAppService"), report);
    assertTrue(report.contains("OrderManager"), report);
  }
}
