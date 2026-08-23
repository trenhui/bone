package com.bone.platform.alert.architecture;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

/**
 * bone-notification 架构守护（应用模块，§14.4 判定：含 NotificationController 业务 API）。
 *
 * <p>按主文档 §21 最小规则集落地；命名/事务规则 freeze 存量（当前无 Command/QueryHandler 类时自然空过）。
 */
@AnalyzeClasses(
    packages = "com.bone.platform.alert",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

  // P0-1
  @ArchTest
  static final ArchRule domain_independent =
      BoneDddArchRules.domainMustNotDependOnOuterLayers().allowEmptyShould(true);

  @ArchTest
  static final ArchRule application_no_infra =
      BoneDddArchRules.applicationMustNotDependOnInfrastructure();

  // P0-5
  @ArchTest
  static final ArchRule domain_no_query_builder =
      BoneDddArchRules.domainMustNotUseQueryBuilder().allowEmptyShould(true);

  // P0-6
  @ArchTest
  static final ArchRule command_no_query_builder =
      BoneDddArchRules.commandHandlersMustNotUseQueryBuilder().allowEmptyShould(true);

  // P0-4 + §18.2
  @ArchTest
  static final ArchRule repository_methods_whitelist =
      BoneDddArchRules.domainRepositoriesShouldOnlyDeclareWhitelistedMethods();

  // P0-7 + §14.3
  @ArchTest
  static final ArchRule no_new_use_cases =
      FreezingArchRule.freeze(BoneDddArchRules.noUseCaseClassesInApplication());

  @ArchTest
  static final ArchRule no_usecase_package =
      FreezingArchRule.freeze(BoneDddArchRules.noApplicationUseCasePackage());

  @ArchTest
  static final ArchRule no_bone_core_usecase =
      BoneDddArchRules.noBoneCoreUseCaseApiDependency().allowEmptyShould(true);

  // §14.5
  @ArchTest
  static final ArchRule no_new_domain_store =
      FreezingArchRule.freeze(BoneDddArchRules.noNewDomainStorePackage());

  // §16.3
  @ArchTest
  static final ArchRule no_custom_business_exception =
      FreezingArchRule.freeze(BoneDddArchRules.noCustomBusinessException());

  @ArchTest
  static final ArchRule no_business_exception_suffix =
      FreezingArchRule.freeze(BoneDddArchRules.noBusinessExceptionSuffix());

  // P0-7 + §15 + §23
  @ArchTest
  static final ArchRule adapter_no_application_service =
      BoneDddArchRules.adapterControllersMustNotDependOnApplicationService();

  @ArchTest
  static final ArchRule adapter_no_domain_repository =
      BoneDddArchRules.adapterControllersMustNotDependOnDomainRepository();

  @ArchTest
  static final ArchRule adapter_no_domain_service =
      BoneDddArchRules.adapterControllersMustNotDependOnDomainService();

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
