package com.bone.metadata.engine.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * bone-metadata-engine 架构守护（增量门禁）。
 *
 * <p>本测试只固化<b>新端口包 {@code spi}</b>的纯净性，作为 change {@code refactor/metadata-engine-boundary-ddd}
 * 的边界基线，不触碰现有引擎代码（避免误判）。
 *
 * <p>随 T2（领域模型归一）推进，将逐步接入 {@code BoneDddArchRules} 全套规则固化四层依赖方向。
 */
@AnalyzeClasses(
    packages = "com.bone.metadata.engine",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class EngineArchitectureTest {

  private static final ArchRule SPI_MUST_NOT_DEPEND_ON_SPRING =
      noClasses()
          .that()
          .resideInAPackage("..spi..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..");

  private static final ArchRule SPI_MUST_NOT_DEPEND_ON_SDK =
      noClasses()
          .that()
          .resideInAPackage("..spi..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.bone.metadata.sdk..");

  private static final ArchRule PORT_CONTRACT_PRESENT =
      classes()
          .that()
          .resideInAPackage("..spi..")
          .and()
          .haveSimpleName("MetadataRepositoryPort")
          .or()
          .resideInAPackage("..spi..")
          .and()
          .haveSimpleName("MetadataPlatformBridge")
          .should()
          .beInterfaces();

  @ArchTest
  void spi_must_not_depend_on_spring(JavaClasses classes) {
    SPI_MUST_NOT_DEPEND_ON_SPRING.check(classes);
  }

  @ArchTest
  void spi_must_not_depend_on_sdk(JavaClasses classes) {
    SPI_MUST_NOT_DEPEND_ON_SDK.check(classes);
  }

  @ArchTest
  void port_contract_present(JavaClasses classes) {
    PORT_CONTRACT_PRESENT.check(classes);
  }
}
