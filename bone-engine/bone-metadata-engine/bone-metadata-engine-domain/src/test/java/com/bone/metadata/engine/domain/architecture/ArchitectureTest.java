package com.bone.metadata.engine.domain.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

/** bone-metadata-engine-domain 架构守护：领域层零依赖（不依赖 ports/runtime/starter）。 */
@AnalyzeClasses(
    packages = "com.bone.metadata.engine.domain",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

  @ArchTest
  static final ArchRule domain_does_not_depend_on_outer_layers =
      ArchRuleDefinition.noClasses()
          .that()
          .resideInAPackage("com.bone.metadata.engine.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.bone.metadata.engine.ports..",
              "com.bone.metadata.engine.runtime..",
              "com.bone.metadata.engine.starter..");
}
