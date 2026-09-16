package com.bone.blueprint;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

/**
 * bone-blueprint 架构守护（参考样板）。
 *
 * <p>真源：{@code doc/architecture/Bone-DDD-最终实践方案.md} E-3（R1–R9 铁律）+ G-1（ArchUnit 规则集 #1–#22）。 共享规则在
 * {@link BoneDddArchRules}，所有应用模块复用同一份。
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

  // §3.1：聚合身份/租户归属不可被外层篡改。
  // 框架侧 AggregateRoot.setId / TenantAggregateRoot.setTenantId 因 SDK 反射回填与 Tenantable 契约
  // 必须保持 public，故改为约束调用方：只有 domain 内部（工厂方法/聚合行为）可设置，杜绝越权改租户。
  @ArchTest
  static final ArchRule no_outer_aggregate_identity_mutation =
      BoneDddArchRules.outerLayersMustNotMutateAggregateIdentity();

  // P0-5：domain 不可使用 QueryBuilder
  @ArchTest
  static final ArchRule domain_no_query_builder = BoneDddArchRules.domainMustNotUseQueryBuilder();

  // P0-6：CommandHandler 禁用 QueryBuilder
  @ArchTest
  static final ArchRule command_no_query_builder =
      BoneDddArchRules.commandHandlersMustNotUseQueryBuilder();

  // E-9.3（v4.6 主判据）：读侧 DSL 只许出现在 infrastructure/query，application 层禁止依赖。
  // 参考样板不 freeze，须 0 违规（分页查询已迁移到 OrderReadPort）。
  @ArchTest
  static final ArchRule read_side_dsl_only_in_query_layer =
      BoneDddArchRules.readSideDslOnlyInQueryLayer();

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
      BoneDddArchRules.adapterControllersMustNotDependOnGodObjects();

  @ArchTest
  static final ArchRule adapter_no_domain_repository =
      BoneDddArchRules.adapterControllersMustNotDependOnDomainRepository();

  @ArchTest
  static final ArchRule adapter_no_domain_service =
      BoneDddArchRules.adapterControllersMustNotDependOnDomainService();

  // E-4.1.1：跨上下文 domain 越界守护；空匹配视为配置错误
  @ArchTest
  static final ArchRule no_cross_context_domain =
      BoneDddArchRules.noCrossContextDomainDependency("com.bone.blueprint");

  // E-4.1.1（v4.6）：全模块禁止依赖其它 Bone 上下文的 domain 模型——
  // 覆盖读侧 QueryHandler 直用 QueryBuilder.from(其它上下文实体) 的穿透路径。
  // 参考样板不 freeze，须 0 违规；空匹配视为配置错误。
  @ArchTest
  static final ArchRule no_cross_context_model =
      BoneDddArchRules.noCrossContextModelDependency("com.bone.blueprint");

  // E-5.3.1（v4.6 内容禁令）：application/service 只许用例级编排，
  // 不得 new 领域对象、不得调聚合 setter 改状态（须经工厂方法/仓储与领域行为方法）。
  @ArchTest
  static final ArchRule application_services_no_domain_rules =
      BoneDddArchRules.applicationServicesMustNotOwnDomainRules();

  // R9（v4.6 + v4.7 修正）：一事务一聚合；扫描范围含 application/service 与 orchestration。
  // 参考样板不 freeze，须 0 违规。
  @ArchTest
  static final ArchRule one_aggregate_per_transaction =
      BoneDddArchRules.oneAggregatePerTransaction();

  // E-4.4（v4.7 补门禁）：租户取值收敛到 TenantProvider 端口，业务层禁止直调 TenantContext。
  // 参考样板不 freeze，须 0 违规。
  @ArchTest
  static final ArchRule tenant_context_via_provider =
      BoneDddArchRules.businessLayersMustNotReadTenantContextDirectly();

  // E-4.3 / E-10.2（#5 回归门禁）：domain/gateway 只允许合法 Domain Gateway（业务语言外部能力，
  // 如账户余额/库存这类业务规则依赖的外部事实）。技术端口（MQ 投递、Outbox 写、Outbox 中继、租户上下文、
  // 缓存、时钟、通知、文件、幂等等）一律不得进入，必须落 application/port/out。
  // 白名单即当前合规的全部 Domain Gateway；新增业务网关须同步更新此白名单，否则 CI 拦截。
  @ArchTest
  static final ArchRule domain_gateway_only_business_gateways =
      classes()
          .that()
          .resideInAPackage("..domain.gateway..")
          .should()
          .haveSimpleName("PaymentGateway")
          .orShould()
          .haveSimpleName("InventoryGateway")
          .orShould()
          .haveSimpleName("PaymentSignaturePort");

  // v4.5：命名 / 事务四条规则已降级为 warn（tasks 2.5），不再作为 @ArchTest 硬门禁；
  // 反贫血主判据切换为 R8 聚合纯单测（AggregatePureUnitTestCoverageTest）。
}
