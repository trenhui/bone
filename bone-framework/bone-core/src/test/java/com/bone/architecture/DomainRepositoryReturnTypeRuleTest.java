package com.bone.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.architecture.fixture.domain.repository.OrderAggregateRepository;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * E-9.2 主判据：写侧仓储返回类型门禁（M2 2.1）的单元测试。
 *
 * <p>测试置于 bone-core（而非 bone-architecture-test）：后者被 bone-core 以 test 作用域依赖，若反向 再加 bone-core 依赖会构成
 * Maven reactor 环。
 */
class DomainRepositoryReturnTypeRuleTest {

  private static final ArchRule RULE =
      BoneDddArchRules.domainRepositoriesShouldOnlyDeclareWhitelistedMethods();

  private static final JavaClasses FIXTURES =
      new ClassFileImporter().importPackages("com.bone.architecture.fixture");

  @Test
  void aggregateReturningAndCompositeKeyMethodsAreAllowed() {
    JavaClasses good = new ClassFileImporter().importClasses(OrderAggregateRepository.class);
    EvaluationResult result = RULE.evaluate(good);
    assertFalse(result.hasViolation(), () -> result.getFailureReport().toString());
  }

  @Test
  void projectionAndPersistenceVocabularyAreRejected() {
    EvaluationResult result = RULE.evaluate(FIXTURES);
    assertTrue(result.hasViolation());
    List<String> details = result.getFailureReport().getDetails();

    // 返回 DTO 投影 → 违规
    assertTrue(
        details.stream().anyMatch(m -> m.contains("findSummary") && m.contains("OrderSummaryDto")),
        () -> details.toString());
    // 返回 List<聚合> → 违规
    assertTrue(
        details.stream().anyMatch(m -> m.contains("findByStatus") && m.contains("List")),
        () -> details.toString());
    // 持久化词汇入方法名 → 违规
    assertTrue(
        details.stream()
            .anyMatch(m -> m.contains("updateStatus") && m.contains("persistence vocabulary")),
        () -> details.toString());
    // 合法方法（复合自然键 / Optional<聚合>）不误报
    assertTrue(
        details.stream().noneMatch(m -> m.contains("findByIdInTenant")), () -> details.toString());
    assertTrue(details.stream().noneMatch(m -> m.contains("findByCode")), () -> details.toString());
  }
}
