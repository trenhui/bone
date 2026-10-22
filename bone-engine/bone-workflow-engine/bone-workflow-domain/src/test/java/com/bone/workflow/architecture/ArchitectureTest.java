package com.bone.workflow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 审批流引擎 domain 层架构守护。
 *
 * <p>判据：domain 必须是<b>纯逻辑</b>——不依赖 Spring、不依赖平台元数据引擎、不依赖外层编排。否则「引擎可独立复用」 这条设计前提会被悄悄破坏（某天有人在 domain
 * 里 @Autowired 一个平台 bean，整个模块就只能被平台用了）。
 */
@AnalyzeClasses(
    packages = "com.bone.workflow.domain",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule domain_must_not_depend_on_outer_or_platform =
      noClasses()
          .that()
          .resideInAPackage("com.bone.workflow.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.bone.workflow.ports..",
              "com.bone.workflow.runtime..",
              "com.bone.workflow.starter..",
              "org.springframework..",
              "com.bone.metadata..")
          .because("domain 须为纯逻辑：不依赖 Spring、不依赖平台元数据引擎、不依赖外层编排");
}
