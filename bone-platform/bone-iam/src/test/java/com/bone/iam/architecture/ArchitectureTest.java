package com.bone.iam.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

@AnalyzeClasses(packages = "com.bone.iam", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

  /** 本模块域仓储包：全租户入口门禁（事实判据 + 命名绑定）的作用域。 */
  private static final String DOMAIN_REPOSITORY_PACKAGE = "com.bone.iam.domain.repository";

  // E-2 / E-4.4 补充门禁（2026-09-20，共享规则）：全租户扫描只许 adapter.schedule 调用，schedule 也只许调全租户入口；
  // 全租户入口必须叫 *AllTenants。判据是事实（@TenantScope(ALL) 或 Criteria.disableTenantFilter()），不是名字。
  @ArchTest
  static final ArchRule all_tenants_scan_only_by_registered_callers =
      BoneDddArchRules.allTenantEntryPointsOnlyCalledBy(
          DOMAIN_REPOSITORY_PACKAGE,
          BoneDddArchRules.AllTenantCallers.ofPackages("..adapter.schedule..")
              // 登录前置定位：认证之前租户未知，必须按用户名跨租户查账号（只限登录入口，见
              // AccountRepository#findByUsernameForLoginAllTenants 的 JavaDoc）。该调用现已内联进
              // AuthApplicationService（application/service 子包废止，ADR-0033 撤销）。
              .andClasses("com.bone.iam.application.AuthApplicationService"));

  @ArchTest
  static final ArchRule schedule_only_calls_all_tenants_repository_methods =
      BoneDddArchRules.scheduleOnlyCallsAllTenantScanMethods(DOMAIN_REPOSITORY_PACKAGE);

  @ArchTest
  static final ArchRule all_tenant_entry_points_must_be_named_all_tenants =
      BoneDddArchRules.allTenantEntryPointsMustBeNamedAllTenants(DOMAIN_REPOSITORY_PACKAGE);

  @ArchTest
  static final ArchRule no_new_use_cases =
      FreezingArchRule.freeze(BoneDddArchRules.noUseCaseClassesInApplication());

  @ArchTest
  static final ArchRule no_usecase_package =
      FreezingArchRule.freeze(BoneDddArchRules.noApplicationUseCasePackage());

  @ArchTest
  static final ArchRule no_bone_core_usecase = BoneDddArchRules.noBoneCoreUseCaseApiDependency();

  // 《Bone-DDD》§14.5
  @ArchTest
  static final ArchRule no_new_domain_store =
      FreezingArchRule.freeze(BoneDddArchRules.noNewDomainStorePackage());

  // 《Bone-DDD》§16.3
  @ArchTest
  static final ArchRule no_custom_business_exception =
      FreezingArchRule.freeze(BoneDddArchRules.noCustomBusinessException());

  @ArchTest
  static final ArchRule no_business_exception_suffix =
      FreezingArchRule.freeze(BoneDddArchRules.noBusinessExceptionSuffix());

  // CORE-02：application -> infrastructure
  @ArchTest
  static final ArchRule application_no_infra =
      BoneDddArchRules.applicationMustNotDependOnInfrastructure();

  // CORE-05：读侧 DSL 边界
  //
  // ADR-0030 后 domain/repository 的 default 方法可承载「本聚合读」（Criteria 就地构造），
  // E-4.1 已把该口径写成平台规则。
  // 2026-09-20：原先"不改共享规则、各模块本地重写"的做法收敛回共享规则——当时担心的
  // "一次性放宽所有模块"经复核不成立（用严格规则的模块在 domain 里本就没有 DSL 依赖，
  // 相关冻结基线全为 0 字节），而同一天内 blueprint / iam / system 三处已各自复制该豁免、
  // integration 与 masterdata 又先后踩到同一面墙。豁免只作用于 ..domain.repository..，其余 domain 包照旧禁止。
  @ArchTest
  static final ArchRule domain_no_query_builder = BoneDddArchRules.domainMustNotUseQueryBuilder();

  // Q1-C 后 `..application.command.handler..` 包已随 Handler 全量内联而消失，规则目标为空 ⇒ allowEmptyShould
  // 保留该规则作为「有人再引入 CommandHandler 并依赖读侧 DSL」的回归门禁（而非删规则）。
  // ApplicationService 自身的读侧 DSL 边界由下方 read_side_dsl_only_in_query_adapter 覆盖。
  @ArchTest
  static final ArchRule command_no_query_builder =
      FreezingArchRule.freeze(
          BoneDddArchRules.commandHandlersMustNotUseQueryBuilder().allowEmptyShould(true));

  @ArchTest
  static final ArchRule read_side_dsl_only_in_query_adapter =
      BoneDddArchRules.readSideDslOnlyInQueryLayer();

  @ArchTest
  static final ArchRule business_layers_no_direct_tenant_context =
      FreezingArchRule.freeze(BoneDddArchRules.businessLayersMustNotReadTenantContextDirectly());

  // E-5.4（P0-3）：application 层 Repository.save() 必须配 publishFrom() 或声明 @NoDomainEvent。
  // iam 当前所有含 save 的应用服务已声明 @NoDomainEvent（事件系统半成品已清理，见 Task #7）。
  @ArchTest
  static final ArchRule application_save_must_pair_with_publish_or_exempt =
      BoneDddArchRules.applicationSaveMustPairWithPublishOrExempt();

  // CORE-02 + E-6（A 类强制，不 freeze）：禁外层篡改聚合 setId / setTenantId
  @ArchTest
  static final ArchRule aggregate_identity_immutable =
      BoneDddArchRules.outerLayersMustNotMutateAggregateIdentity();

  // §18.2：仓储方法白名单
  @ArchTest
  static final ArchRule repository_methods_whitelist =
      BoneDddArchRules.domainRepositoriesShouldOnlyDeclareWhitelistedMethods();

  @ArchTest
  static void domainLayerShouldNotDependOnOuterLayers(JavaClasses classes) {
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..adapter..", "..application..", "..infrastructure..")
        .check(classes);
  }

  @ArchTest
  static void domainCoreShouldOnlyDependOnAllowedPackages(JavaClasses classes) {
    classes()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(
            "..domain..",
            "java..",
            "com.bone.core..",
            "com.bone.metadata.sdk..",
            "lombok..",
            "org.springframework.lang..")
        .check(classes);
  }

  @ArchTest
  static void repositoryInterfacesShouldBeInDomain(JavaClasses classes) {
    classes()
        .that()
        .haveNameMatching(".*Repository")
        .and()
        .areInterfaces()
        .should()
        .resideInAPackage("..domain..")
        .check(classes);
  }

  @ArchTest
  static void repositoryImplementationsShouldBeInInfrastructure(JavaClasses classes) {
    classes()
        .that()
        .haveNameMatching(".*RepositoryImpl")
        .should()
        .resideInAPackage("..infrastructure..")
        .allowEmptyShould(true)
        .check(classes);
  }

  // 原 domainRepositoriesShouldNotDeclareCustomMethods（禁止域仓储声明任何方法）已删除：
  // ADR-0030 采纳后，域仓储承载「本聚合读」，default 方法正是规范指定的落地形态（§E-4.1 / ADR-0030 §P4），
  // 该规则与规范直接冲突；其职责已由共享规则 repository_methods_whitelist（按返回类型判定：
  // 聚合 / Optional<聚合> / 标量 / 域内投影 合规）覆盖，保留两份口径会互相矛盾。

  // CORE-04 + §15 + §23（存量 freeze，迁移后收缩基线）
  @ArchTest
  static final ArchRule adapter_no_application_service =
      FreezingArchRule.freeze(BoneDddArchRules.adapterControllersMustNotDependOnGodObjects());

  @ArchTest
  static final ArchRule adapter_no_domain_repository =
      FreezingArchRule.freeze(BoneDddArchRules.adapterControllersMustNotDependOnDomainRepository());

  @ArchTest
  static final ArchRule adapter_no_domain_service =
      FreezingArchRule.freeze(BoneDddArchRules.adapterControllersMustNotDependOnDomainService());

  // v4.5：命名 / 事务四条规则已降级为 warn（tasks 2.5），不再作为 @ArchTest 硬门禁；
  // 聚合纯单测卫生检查使用 TEST-HYGIENE-01（AggregatePureUnitTestCoverageTest）。

  // P-2.3 + P-2.4（D9）：跨上下文 domain 越界守护；空匹配视为配置错误
  @ArchTest
  static final ArchRule no_cross_context_domain =
      BoneDddArchRules.noCrossContextDomainDependency("com.bone.iam");

  // 《BONE 总体架构设计方案》§4.1.1：模块层级依赖方向（ARCH-LEVEL-01/02）
  @ArchTest
  static final ArchRule engine_no_platform =
      BoneDddArchRules.engineModulesMustNotDependOnPlatform();

  @ArchTest
  static final ArchRule platform_no_engine_apps =
      BoneDddArchRules.platformMustNotDependOnEngineApps();
}
