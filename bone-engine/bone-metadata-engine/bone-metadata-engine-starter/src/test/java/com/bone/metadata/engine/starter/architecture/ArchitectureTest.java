package com.bone.metadata.engine.starter.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

/** bone-metadata-engine-starter 架构守护：自动装配层仅做装配，不得依赖 runtime 之外的其它内部实现。 */
@AnalyzeClasses(
    packages = "com.bone.metadata.engine.starter",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

  @ArchTest
  static final ArchRule starter_config_does_not_depend_on_core =
      ArchRuleDefinition.noClasses()
          .that()
          .resideInAPackage("com.bone.metadata.engine.starter..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.bone.metadata.engine.core..");
}
