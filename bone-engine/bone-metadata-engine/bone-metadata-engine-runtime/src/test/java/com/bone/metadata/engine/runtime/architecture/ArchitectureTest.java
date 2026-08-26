package com.bone.metadata.engine.runtime.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

/** bone-metadata-engine-runtime 架构守护：运行时层依赖 domain+ports，不依赖 starter（上层）。 */
@AnalyzeClasses(
    packages = "com.bone.metadata.engine.runtime",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

  @ArchTest
  static final ArchRule runtime_does_not_depend_on_starter =
      ArchRuleDefinition.noClasses()
          .that()
          .resideInAPackage("com.bone.metadata.engine.runtime..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.bone.metadata.engine.starter..");
}
