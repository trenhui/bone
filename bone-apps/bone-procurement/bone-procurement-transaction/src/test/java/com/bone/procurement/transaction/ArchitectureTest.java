package com.bone.procurement.transaction;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 采购交易上下文架构门禁（《Bone-DDD》§21 / G-1，复用 bone-architecture-test 共享规则）。
 *
 * <p>本期为单库本地事务切片，覆盖 DDD 四层依赖方向、仓储白名单、异常纪律、事务与租户收敛等 与 synap-claim 同口径的硬规则。@NoDomainEvent 用于豁免「save
 * 必配 publish」（本期不发领域事件）。
 */
@AnalyzeClasses(
    packages = "com.bone.procurement.transaction",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

  // P0-1：依赖方向
  @ArchTest
  static final ArchRule domain_independent = BoneDddArchRules.domainMustNotDependOnOuterLayers();

  @ArchTest
  static final ArchRule application_no_infra =
      BoneDddArchRules.applicationMustNotDependOnInfrastructure();

  // §15：adapter 入站不得直注 application 服务对象 / domain.repository
  @ArchTest
  static final ArchRule adapter_no_application_service =
      BoneDddArchRules.adaptersMustNotDependOnGodObjects();

  @ArchTest
  static final ArchRule adapter_no_domain_repository =
      BoneDddArchRules.adaptersMustNotDependOnDomainRepository();

  // P0-4：仓储方法白名单
  @ArchTest
  static final ArchRule repository_methods_whitelist =
      BoneDddArchRules.domainRepositoriesShouldOnlyDeclareWhitelistedMethods();

  // §14.3：禁止模块自建 BusinessException（统一用平台 BizException + ProcurementErrors 工厂）
  @ArchTest
  static final ArchRule no_custom_business_exception = BoneDddArchRules.noCustomBusinessException();

  @ArchTest
  static final ArchRule no_business_exception_suffix = BoneDddArchRules.noBusinessExceptionSuffix();

  // E-2：租户取值收敛到 TenantPort，业务层禁止直读 TenantContext
  @ArchTest
  static final ArchRule tenant_context_via_provider =
      BoneDddArchRules.businessLayersMustNotReadTenantContextDirectly();

  // E-5.4：application 层 Repository.save() 必须配 publishFrom() 或 @NoDomainEvent（本模块已声明）
  @ArchTest
  static final ArchRule save_must_pair_with_publish_or_exempt =
      BoneDddArchRules.applicationSaveMustPairWithPublishOrExempt();

  // E-13.0：Spring bean 名唯一
  @ArchTest
  static final ArchRule spring_bean_names_unique =
      BoneDddArchRules.springComponentBeanNamesMustBeUnique();

  // 跨上下文 domain 越界守护（本切片 domain 只依赖平台共享内核，不依赖其他业务上下文）
  @ArchTest
  static final ArchRule no_cross_context_domain =
      BoneDddArchRules.noCrossContextDomainDependency("com.bone.procurement.transaction");

  /**
   * 安全门禁：每个 HTTP 端点都必须声明 {@code @PreAuthorize}。
   *
   * <p>用 {@code areMetaAnnotatedWith(RequestMapping)} 覆盖 @GetMapping/@PostMapping 等组合注解； 刻意不设
   * allowEmptyShould——扫不到端点时转红，防止规则静默失效。
   */
  @ArchTest
  static final ArchRule controller_endpoints_must_declare_preauthorize =
      methods()
          .that()
          .areDeclaredInClassesThat()
          .resideInAPackage("..adapter.web.controller..")
          .and()
          .areMetaAnnotatedWith(RequestMapping.class)
          .should()
          .beAnnotatedWith(PreAuthorize.class)
          .because("每个采购端点都必须声明权限码，否则任意已认证用户即可调用");

  // adapter 全包不得依赖 domain.repository（授权边界不放开，本期无 schedule 入站）
  @ArchTest
  static final ArchRule adapter_no_domain_repository_all_packages =
      noClasses()
          .that()
          .resideInAPackage("..adapter..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..domain.repository..")
          .allowEmptyShould(true)
          .because("入站适配器只许依赖 application，不得绕过应用服务直连仓储");
}
