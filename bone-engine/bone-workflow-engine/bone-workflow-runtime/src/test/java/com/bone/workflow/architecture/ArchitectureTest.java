package com.bone.workflow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 审批流引擎 runtime 层架构守护。
 *
 * <p>判据：runtime <b>不得依赖平台元数据引擎</b>。引擎与平台之间只允许通过 ports 契约对话——一旦 runtime 里出现 对 {@code
 * bone-metadata-engine} 的引用，引擎就退化成「只能在平台里跑的一段代码」，失去被采购、报销、其它场景 复用的能力。
 */
@AnalyzeClasses(
    packages = "com.bone.workflow.runtime",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule runtime_must_not_depend_on_platform_metadata_engine =
      noClasses()
          .that()
          .resideInAPackage("com.bone.workflow.runtime..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.bone.metadata..", "com.bone.metadata.sdk..")
          .because("引擎与平台只经 ports 契约对话，不得直连平台元数据引擎（否则无法被其它场景复用）");
}
