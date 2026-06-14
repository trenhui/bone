package com.bone.masterdata.architecture;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

/** bone-masterdata 架构守护 — 统一来自 {@link BoneDddArchRules}（《Bone-DDD》§21 附录 B.3）。 */
@AnalyzeClasses(
    packages = "com.bone.masterdata",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

  @ArchTest
  static final ArchRule domain_independent = BoneDddArchRules.domainMustNotDependOnOuterLayers();

  @ArchTest
  static final ArchRule application_no_infra =
      BoneDddArchRules.applicationMustNotDependOnInfrastructure();

  @ArchTest
  static final ArchRule domain_no_query_builder = BoneDddArchRules.domainMustNotUseQueryBuilder();

  @ArchTest
  static final ArchRule command_no_query_builder =
      BoneDddArchRules.commandHandlersMustNotUseQueryBuilder();

  @ArchTest
  static final ArchRule repository_methods_whitelist =
      BoneDddArchRules.domainRepositoriesShouldOnlyDeclareWhitelistedMethods();

  @ArchTest
  static final ArchRule no_new_use_cases =
      FreezingArchRule.freeze(BoneDddArchRules.noUseCaseClassesInApplication());

  @ArchTest
  static final ArchRule no_usecase_package =
      FreezingArchRule.freeze(BoneDddArchRules.noApplicationUseCasePackage());

  @ArchTest
  static final ArchRule no_bone_core_usecase = BoneDddArchRules.noBoneCoreUseCaseApiDependency();

  @ArchTest
  static final ArchRule no_new_domain_store =
      FreezingArchRule.freeze(BoneDddArchRules.noNewDomainStorePackage());

  @ArchTest
  static final ArchRule no_custom_business_exception =
      FreezingArchRule.freeze(BoneDddArchRules.noCustomBusinessException());

  @ArchTest
  static final ArchRule no_business_exception_suffix =
      FreezingArchRule.freeze(BoneDddArchRules.noBusinessExceptionSuffix());

  // P0-7 + §15 + §23（存量 freeze，迁移后收缩基线）
  @ArchTest
  static final ArchRule adapter_no_application_service =
      FreezingArchRule.freeze(
          BoneDddArchRules.adapterControllersMustNotDependOnApplicationService());

  @ArchTest
  static final ArchRule adapter_no_domain_repository =
      FreezingArchRule.freeze(BoneDddArchRules.adapterControllersMustNotDependOnDomainRepository());

  @ArchTest
  static final ArchRule adapter_no_domain_service =
      FreezingArchRule.freeze(BoneDddArchRules.adapterControllersMustNotDependOnDomainService());

  @ArchTest
  static final ArchRule command_handler_naming =
      FreezingArchRule.freeze(BoneDddArchRules.commandHandlersShouldBeNamedCommandHandler());

  @ArchTest
  static final ArchRule query_handler_naming =
      FreezingArchRule.freeze(BoneDddArchRules.queryHandlersShouldBeNamedQueryHandler());

  @ArchTest
  static final ArchRule command_handler_transactional =
      FreezingArchRule.freeze(BoneDddArchRules.commandHandlersShouldBeTransactional());

  @ArchTest
  static final ArchRule query_handler_transactional =
      FreezingArchRule.freeze(BoneDddArchRules.queryHandlersShouldBeReadOnlyTransactional());
}
