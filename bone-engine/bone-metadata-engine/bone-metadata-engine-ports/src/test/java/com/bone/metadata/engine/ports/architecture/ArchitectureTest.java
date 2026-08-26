package com.bone.metadata.engine.ports.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

/** bone-metadata-engine-ports 架构守护：端口层仅依赖 domain，不依赖 runtime/starter。 */
@AnalyzeClasses(
    packages = "com.bone.metadata.engine.ports",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

  @ArchTest
  static final ArchRule ports_do_not_depend_on_runtime_or_starter =
      ArchRuleDefinition.noClasses()
          .that()
          .resideInAPackage("com.bone.metadata.engine.ports..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.bone.metadata.engine.runtime..", "com.bone.metadata.engine.starter..");
}
