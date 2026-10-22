package com.bone.claim;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * synap-claim 架构门禁（复用 bone-architecture-test 共享规则，《Bone-DDD》§21 / G-1）。
 *
 * <p>覆盖 blueprint 同款全集：依赖方向、聚合身份不可外层篡改、domain 禁 QueryBuilder、仓储方法白名单、 adapter 不直注
 * domain.repository/service、 一事务一聚合、save 必配 publish、全租户仅 schedule、租户经 TenantPort、端口实现 *PortAdapter
 * 命名。
 */
@AnalyzeClasses(packages = "com.bone.claim", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

  /** 本模块域仓储包：全租户入口的三条共享规则都以此包为作用域。 */
  private static final String DOMAIN_REPOSITORY_PACKAGE = "com.bone.claim.domain.repository";

  // P0-1：依赖方向
  @ArchTest
  static final ArchRule domain_independent = BoneDddArchRules.domainMustNotDependOnOuterLayers();

  @ArchTest
  static final ArchRule application_no_infra =
      BoneDddArchRules.applicationMustNotDependOnInfrastructure();

  // §3.1：聚合身份/租户归属不可被外层篡改
  @ArchTest
  static final ArchRule no_outer_aggregate_identity_mutation =
      BoneDddArchRules.outerLayersMustNotMutateAggregateIdentity();

  // P0-5：domain 不可使用 QueryBuilder（豁免 ..domain.repository..）
  @ArchTest
  static final ArchRule domain_no_query_builder = BoneDddArchRules.domainMustNotUseQueryBuilder();

  // E-4.2：读侧 DSL 不得进入 application
  @ArchTest
  static final ArchRule read_side_dsl_only_in_query_layer =
      BoneDddArchRules.readSideDslOnlyInQueryLayer();

  // P0-4 + §18.2：仓储方法白名单
  @ArchTest
  static final ArchRule repository_methods_whitelist =
      BoneDddArchRules.domainRepositoriesShouldOnlyDeclareWhitelistedMethods();

  // §14.3：禁止模块自建 BusinessException
  @ArchTest
  static final ArchRule no_custom_business_exception = BoneDddArchRules.noCustomBusinessException();

  @ArchTest
  static final ArchRule no_business_exception_suffix = BoneDddArchRules.noBusinessExceptionSuffix();

  // §15：adapter 入站约束
  @ArchTest
  static final ArchRule adapter_no_application_service =
      BoneDddArchRules.adaptersMustNotDependOnGodObjects();

  @ArchTest
  static final ArchRule adapter_no_domain_repository =
      BoneDddArchRules.adaptersMustNotDependOnDomainRepository();

  @ArchTest
  static final ArchRule adapter_no_domain_service =
      BoneDddArchRules.adaptersMustNotDependOnDomainService();

  // P0-1 延伸：adapter 全包（不止 controller）不得依赖 domain.repository；授权边界只开在 adapter.schedule
  @ArchTest
  static final ArchRule adapter_no_domain_repository_all_packages =
      noClasses()
          .that()
          .resideInAPackage("..adapter..")
          .and()
          .resideOutsideOfPackage("..adapter.schedule..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..domain.repository..")
          .allowEmptyShould(true)
          .because(
              "入站适配器（web/rpc/messaging）只许依赖 application；"
                  + "adapter.schedule 是 ADR-0030 授权的平台运维入口，可用面由 "
                  + "schedule_only_calls_all_tenants_repository_methods 收紧");

  // P2-4：全租户扫描仅限 schedule 包调用
  @ArchTest
  static final ArchRule all_tenants_scan_only_by_schedule =
      BoneDddArchRules.allTenantScanMethodsOnlyCalledBySchedule(DOMAIN_REPOSITORY_PACKAGE);

  // P2-5：全租户入口必须叫 *AllTenants，且必须真的绕过租户隔离
  @ArchTest
  static final ArchRule all_tenant_entry_points_must_be_named_all_tenants =
      BoneDddArchRules.allTenantEntryPointsMustBeNamedAllTenants(DOMAIN_REPOSITORY_PACKAGE);

  // P2-4 收紧：schedule 拿到域仓储后也只许调 *AllTenants 方法
  @ArchTest
  static final ArchRule schedule_only_calls_all_tenants_repository_methods =
      BoneDddArchRules.scheduleOnlyCallsAllTenantScanMethods(DOMAIN_REPOSITORY_PACKAGE);

  // E-1.3：跨上下文 domain 越界守护
  @ArchTest
  static final ArchRule no_cross_context_domain =
      BoneDddArchRules.noCrossContextDomainDependency("com.bone.claim");

  @ArchTest
  static final ArchRule no_cross_context_model =
      BoneDddArchRules.noCrossContextModelDependency("com.bone.claim");

  // E-6.4 / CORE-03：application 只许用例级编排，不得 new 领域对象/调 setter 改状态
  @ArchTest
  static final ArchRule application_services_no_domain_rules =
      BoneDddArchRules.applicationServicesMustNotOwnDomainRules();

  // CORE-06 / E-5.1：一事务一聚合
  @ArchTest
  static final ArchRule one_aggregate_per_transaction =
      BoneDddArchRules.oneAggregatePerTransaction();

  // E-2：租户取值收敛到 TenantPort，业务层禁止直调 TenantContext
  @ArchTest
  static final ArchRule tenant_context_via_provider =
      BoneDddArchRules.businessLayersMustNotReadTenantContextDirectly();

  // E-13.3：infrastructure 实现 application.port.out.*Port 的类必须命名 *PortAdapter
  @ArchTest
  static final ArchRule port_implementations_must_end_with_port_adapter =
      classes()
          .that()
          .resideInAPackage("..infrastructure..")
          .and()
          .implement(applicationPortInterface())
          .should()
          .haveSimpleNameEndingWith("PortAdapter")
          .allowEmptyShould(true)
          .because("E-13.3: application Port implementations must be named *PortAdapter");

  // E-5.4：application 层 Repository.save() 必须配 publishFrom() 或声明 @NoDomainEvent
  @ArchTest
  static final ArchRule save_must_pair_with_publish_or_exempt =
      BoneDddArchRules.applicationSaveMustPairWithPublishOrExempt();

  // E-13.0：Spring bean 名唯一（协议边界后同模块两协议 Controller 可合法同名）
  @ArchTest
  static final ArchRule spring_bean_names_unique =
      BoneDddArchRules.springComponentBeanNamesMustBeUnique();

  private static DescribedPredicate<JavaClass> applicationPortInterface() {
    return new DescribedPredicate<>("implements an application.port.out.*Port interface") {
      @Override
      public boolean test(JavaClass input) {
        for (JavaType t : input.getInterfaces()) {
          if (!(t instanceof JavaClass)) continue;
          JavaClass iface = (JavaClass) t;
          if (iface.getName().startsWith("com.bone.claim.application.port.out.")
              && iface.getSimpleName().endsWith("Port")) {
            return true;
          }
        }
        return false;
      }
    };
  }
}
