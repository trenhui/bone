package com.bone.aiorch.architecture;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

/** bone-ai-orchestration 架构守护（应用层编排，无领域/基础设施实现，规则空过）。 */
@AnalyzeClasses(packages = "com.bone.aiorch", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

  @ArchTest
  static final ArchRule domain_independent =
      BoneDddArchRules.domainMustNotDependOnOuterLayers().allowEmptyShould(true);

  @ArchTest
  static final ArchRule application_not_depend_infrastructure =
      BoneDddArchRules.applicationMustNotDependOnInfrastructure().allowEmptyShould(true);

  @ArchTest
  static final ArchRule app_services_not_own_domain_rules =
      BoneDddArchRules.applicationServicesMustNotOwnDomainRules().allowEmptyShould(true);

  @ArchTest
  static final ArchRule no_custom_business_exception = BoneDddArchRules.noCustomBusinessException();

  @ArchTest
  static final ArchRule no_business_exception_suffix = BoneDddArchRules.noBusinessExceptionSuffix();

  @ArchTest
  static final ArchRule no_bone_core_usecase =
      BoneDddArchRules.noBoneCoreUseCaseApiDependency().allowEmptyShould(true);

  @ArchTest
  static final ArchRule no_usecase_package =
      BoneDddArchRules.noApplicationUseCasePackage().allowEmptyShould(true);

  @ArchTest
  static final ArchRule no_new_domain_store =
      BoneDddArchRules.noNewDomainStorePackage().allowEmptyShould(true);

  @ArchTest
  static final ArchRule tenant_context_not_read_directly =
      BoneDddArchRules.businessLayersMustNotReadTenantContextDirectly().allowEmptyShould(true);

  @ArchTest
  static final ArchRule adapters_not_depend_god_objects =
      BoneDddArchRules.adaptersMustNotDependOnGodObjects().allowEmptyShould(true);

  @ArchTest
  static final ArchRule spring_component_bean_names_unique =
      BoneDddArchRules.springComponentBeanNamesMustBeUnique();

  /**
   * 类型护栏（详设 §3.3 规则 6，2026-10-24 由「目标态」转「已机械强制」）：{@code AiSuggestion} 不得越过 {@code domain.decision}
   * 包边界——应用 / 适配 / Web 层禁引用 AI 建议类型，只允许消费 {@code Confirmed} （HITL 裁决或规则产出）。此前该规则只有工程约定（record
   * 不存在、无拦截对象），「资金 / 责任 / 数值判定只收 Confirmed」审计时无法机械发现；record 落地后本规则即编译期拦截。
   */
  @ArchTest
  static final ArchRule ai_suggestion_confined_to_decision_package =
      ArchRuleDefinition.noClasses()
          .that()
          .resideOutsideOfPackage("com.bone.aiorch.domain.decision..")
          .should()
          .dependOnClassesThat()
          .haveSimpleName("AiSuggestion")
          .because("AI 建议不得越出决策包：业务侧只消费 Confirmed（详设 §3.3 规则 6，类型护栏机械强制）");
}
