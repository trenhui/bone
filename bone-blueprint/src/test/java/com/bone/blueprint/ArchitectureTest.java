package com.bone.blueprint;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

/**
 * bone-blueprint 架构守护（参考样板）。
 *
 * <p>真源：{@code doc/architecture/Bone-DDD-最终实践方案.md} §12（P0 七条）+ §21。 共享规则在 {@link
 * BoneDddArchRules}，所有应用模块复用同一份。
 *
 * <p>首次集成 / 收缩基线见 {@code bone-framework/bone-architecture-test/README.md}。
 */
@AnalyzeClasses(
    packages = "com.bone.blueprint",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

  // P0-1：依赖方向
  @ArchTest
  static final ArchRule domain_independent = BoneDddArchRules.domainMustNotDependOnOuterLayers();

  @ArchTest
  static final ArchRule application_no_infra =
      BoneDddArchRules.applicationMustNotDependOnInfrastructure();

  // P0-5：domain 不可使用 QueryBuilder
  @ArchTest
  static final ArchRule domain_no_query_builder = BoneDddArchRules.domainMustNotUseQueryBuilder();

  // P0-6：CommandHandler 禁用 QueryBuilder
  @ArchTest
  static final ArchRule command_no_query_builder =
      BoneDddArchRules.commandHandlersMustNotUseQueryBuilder();

  // P0-4 + §18.2：仓储方法白名单
  @ArchTest
  static final ArchRule repository_methods_whitelist =
      BoneDddArchRules.domainRepositoriesShouldOnlyDeclareWhitelistedMethods();

  // P0-7 + §14.3：禁止 UseCase（三件套）
  @ArchTest
  static final ArchRule no_new_use_cases =
      FreezingArchRule.freeze(BoneDddArchRules.noUseCaseClassesInApplication());

  @ArchTest
  static final ArchRule no_usecase_package =
      FreezingArchRule.freeze(BoneDddArchRules.noApplicationUseCasePackage());

  @ArchTest
  static final ArchRule no_bone_core_usecase = BoneDddArchRules.noBoneCoreUseCaseApiDependency();

  // §14.5：禁止新增 domain.store 包
  @ArchTest
  static final ArchRule no_new_domain_store =
      FreezingArchRule.freeze(BoneDddArchRules.noNewDomainStorePackage());

  // §16.3：禁止模块自建 BusinessException
  @ArchTest
  static final ArchRule no_custom_business_exception =
      FreezingArchRule.freeze(BoneDddArchRules.noCustomBusinessException());

  @ArchTest
  static final ArchRule no_business_exception_suffix =
      FreezingArchRule.freeze(BoneDddArchRules.noBusinessExceptionSuffix());

  // P0-7 + §15：adapter 入站约束（参考样板不 freeze，须 0 违规）
  @ArchTest
  static final ArchRule adapter_no_application_service =
      BoneDddArchRules.adapterControllersMustNotDependOnApplicationService();

  @ArchTest
  static final ArchRule adapter_no_domain_repository =
      BoneDddArchRules.adapterControllersMustNotDependOnDomainRepository();

  @ArchTest
  static final ArchRule adapter_no_domain_service =
      BoneDddArchRules.adapterControllersMustNotDependOnDomainService();

  // §23 + §15：Handler 命名与事务边界（参考样板不 freeze）
  @ArchTest
  static final ArchRule command_handler_naming =
      BoneDddArchRules.commandHandlersShouldBeNamedCommandHandler();

  @ArchTest
  static final ArchRule query_handler_naming =
      BoneDddArchRules.queryHandlersShouldBeNamedQueryHandler();

  @ArchTest
  static final ArchRule command_handler_transactional =
      BoneDddArchRules.commandHandlersShouldBeTransactional();

  @ArchTest
  static final ArchRule query_handler_transactional =
      BoneDddArchRules.queryHandlersShouldBeReadOnlyTransactional();
}
