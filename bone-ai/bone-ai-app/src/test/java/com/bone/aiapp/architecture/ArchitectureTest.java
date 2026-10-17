package com.bone.aiapp.architecture;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** bone-ai-app 架构守护（聚合根模块，仅含 web/config，DDD 分层规则无匹配类时统一空过）。 */
@AnalyzeClasses(packages = "com.bone.aiapp", importOptions = ImportOption.DoNotIncludeTests.class)
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

  @ArchTest static final ArchRule no_aviator_dependency = BoneDddArchRules.noAviatorDependency();
}
