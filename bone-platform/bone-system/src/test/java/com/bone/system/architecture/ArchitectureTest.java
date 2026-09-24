package com.bone.system.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

/**
 * bone-system 架构守护 — 统一来自 {@link BoneDddArchRules}（《Bone-DDD》§21 附录 B.3）。
 *
 * <p>首次集成或基线收缩命令见 {@code bone-framework/bone-architecture-test/README.md}。
 */
@AnalyzeClasses(packages = "com.bone.system", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

  /** 本模块域仓储包：全租户入口门禁（事实判据 + 命名绑定）的作用域。 */
  private static final String DOMAIN_REPOSITORY_PACKAGE = "com.bone.system.domain.repository";

  // E-2 / E-4.4 补充门禁（2026-09-20，共享规则）：全租户扫描只许 adapter.schedule 调用，schedule 也只许调全租户入口；
  // 全租户入口必须叫 *AllTenants。判据是事实（@TenantScope(ALL) 或 Criteria.disableTenantFilter()），不是名字。
  @ArchTest
  static final ArchRule all_tenants_scan_only_by_schedule =
      BoneDddArchRules.allTenantScanMethodsOnlyCalledBySchedule(DOMAIN_REPOSITORY_PACKAGE);

  @ArchTest
  static final ArchRule schedule_only_calls_all_tenants_repository_methods =
      BoneDddArchRules.scheduleOnlyCallsAllTenantScanMethods(DOMAIN_REPOSITORY_PACKAGE);

  @ArchTest
  static final ArchRule all_tenant_entry_points_must_be_named_all_tenants =
      BoneDddArchRules.allTenantEntryPointsMustBeNamedAllTenants(DOMAIN_REPOSITORY_PACKAGE);

  // CORE-02：依赖向内
  @ArchTest
  static final ArchRule domain_independent = BoneDddArchRules.domainMustNotDependOnOuterLayers();

  @ArchTest
  static final ArchRule application_no_infra =
      BoneDddArchRules.applicationMustNotDependOnInfrastructure();

  // CORE-05：domain 不可使用读侧 DSL（豁免 ..domain.repository.. ——仓储是 SDK 框架集成点。
  // ADR-0030 后本聚合读方法就落在这里：基类 Repository<T,ID> 自带 updateByCriteria(Criteria<T>)，
  // 且 SDK 无级联时子实体读取也需经它。与 bone-blueprint 的豁免口径保持一致。）
  @ArchTest
  static final ArchRule domain_no_query_builder =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .and()
          .resideOutsideOfPackage("..domain.repository..")
          .should()
          .dependOnClassesThat(annotatedWithReadSideOnly())
          .allowEmptyShould(true)
          .because(
              "DDD P0-5: read-side DSL (@ReadSideOnly) must not appear in domain; "
                  + "exception: domain.repository is an SDK framework integration point that "
                  + "inherits updateByCriteria(Criteria<T>) and hosts this-aggregate read methods "
                  + "after ADR-0030");

  // CORE-05（写侧）：本模块已无 `application/command/handler` 包——全部写用例收敛为语义化
  // ApplicationService（ADR-0028），读侧 DSL 也随之下沉到 domain.repository。故不再需要
  // `commandHandlersMustNotUseQueryBuilder`：规则空匹配会直接失败，留着等于把一条永远不生效的规则
  // 挂在构建里。若将来重新引入 CommandHandler，按 E-3.7 决策树判断后同步把这条规则加回来。

  // CORE-02 + E-6（A 类强制，不 freeze）：禁外层篡改聚合 setId / setTenantId
  @ArchTest
  static final ArchRule aggregate_identity_immutable =
      BoneDddArchRules.outerLayersMustNotMutateAggregateIdentity();

  // CORE-05 + §18.2
  @ArchTest
  static final ArchRule repository_methods_whitelist =
      BoneDddArchRules.domainRepositoriesShouldOnlyDeclareWhitelistedMethods();

  // P0-1（R9 / CORE）：一事务一聚合，跨聚合变更须经领域事件 / Outbox / 编排器
  @ArchTest
  static final ArchRule one_aggregate_per_transaction =
      BoneDddArchRules.oneAggregatePerTransaction();

  // P0-2（E-6.4 / R2 反贫血）：application 层不得直接实例化或改领域对象状态
  @ArchTest
  static final ArchRule application_services_no_domain_rules =
      BoneDddArchRules.applicationServicesMustNotOwnDomainRules();

  // P0-3（E-5.4）：application 层 Repository.save() 必须配 publishFrom() 或声明 @NoDomainEvent
  @ArchTest
  static final ArchRule application_save_must_pair_with_publish_or_exempt =
      BoneDddArchRules.applicationSaveMustPairWithPublishOrExempt();

  // CORE-04 + §14.3
  @ArchTest
  static final ArchRule no_new_use_cases =
      FreezingArchRule.freeze(BoneDddArchRules.noUseCaseClassesInApplication());

  @ArchTest
  static final ArchRule no_usecase_package =
      FreezingArchRule.freeze(BoneDddArchRules.noApplicationUseCasePackage());

  @ArchTest
  static final ArchRule no_bone_core_usecase = BoneDddArchRules.noBoneCoreUseCaseApiDependency();

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

  // E-2（v4.7 补门禁）：租户取值收敛到 TenantProvider 端口，业务层（application/domain/adapter）禁止直调 TenantContext。
  // 存量直调先冻结为已知债，禁止新增（与 iam/masterdata 同口径）。
  @ArchTest
  static final ArchRule tenant_context_via_provider =
      FreezingArchRule.freeze(BoneDddArchRules.businessLayersMustNotReadTenantContextDirectly());

  // E-4.2（v4.6 主判据）：读侧 DSL（Criteria / @ReadSideOnly）不得出现在 application 层；DSL 须落在
  // infrastructure/query 或 domain.repository（SDK 框架集成点）。参考样板不 freeze，须 0 违规。
  @ArchTest
  static final ArchRule read_side_dsl_only_in_query_layer =
      BoneDddArchRules.readSideDslOnlyInQueryLayer();

  // E-13.0（v5.6 补门禁）：同模块内 Spring 组件 bean 名必须唯一，提前到构建期捕获启动期
  // ConflictingBeanDefinitionException（G-1.5 已确认全仓零冲突）。
  @ArchTest
  static final ArchRule spring_bean_names_unique =
      BoneDddArchRules.springComponentBeanNamesMustBeUnique();

  // P-2.3 + P-2.4（D9）：跨上下文 domain 越界守护；空匹配视为配置错误
  @ArchTest
  static final ArchRule no_cross_context_domain =
      BoneDddArchRules.noCrossContextDomainDependency("com.bone.system");

  private static com.tngtech.archunit.base.DescribedPredicate<JavaClass>
      annotatedWithReadSideOnly() {
    return new com.tngtech.archunit.base.DescribedPredicate<>("annotated with @ReadSideOnly") {
      @Override
      public boolean test(JavaClass input) {
        return input.isAnnotatedWith("com.bone.core.annotation.ReadSideOnly");
      }
    };
  }
}
