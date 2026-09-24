package com.bone.file.architecture;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** bone-file 架构守护。文件服务为基础设施组件，DDD 分层规则无匹配类时统一空过。 */
@AnalyzeClasses(packages = "com.bone.file", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

  @ArchTest
  static final ArchRule domain_independent =
      BoneDddArchRules.domainMustNotDependOnOuterLayers().allowEmptyShould(true);

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
  static final ArchRule spring_component_bean_names_unique =
      BoneDddArchRules.springComponentBeanNamesMustBeUnique();

  // ARCH-LEVEL-01：引擎模块不得依赖平台（层级方向 Platform -> Engine -> Framework -> Kernel）
  @ArchTest
  static final ArchRule engine_must_not_depend_on_platform =
      BoneDddArchRules.engineModulesMustNotDependOnPlatform();

  // ARCH-LEVEL-02：平台不得依赖引擎可部署应用壳，仅允许依赖 SDK 底座（metadata-sdk / extension-sdk）
  @ArchTest
  static final ArchRule platform_must_not_depend_on_engine_apps =
      BoneDddArchRules.platformMustNotDependOnEngineApps();
}
