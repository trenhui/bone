package com.bone.boot;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * 模块边界治理（ArchUnit）。组合根 {@code bone-boot} 同时依赖平台与各业务模块，是校验分层的最佳位置。
 *
 * <p>固化「平台 / 横向引擎 / 业务应用」的依赖方向，防止业务代码回流平台或业务垂直侵蚀可复用引擎：
 *
 * <ol>
 *   <li>平台（{@code com.bone.metadata}）不得依赖任何业务模块；
 *   <li>会计引擎（{@code com.bone.accounting}）不得依赖具体业务垂直（报销/采购），保证横向可复用；
 *   <li>业务应用（{@code com.bone.app}）不得编译期依赖会计引擎——复用走动作定义 {@code handler_bean} 契约。
 * </ol>
 */
class ModuleBoundaryArchTest {

  private static JavaClasses classes() {
    return new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeTests())
        .importPackages("com.bone");
  }

  @Test
  void platformMustNotDependOnBusinessModules() {
    noClasses()
        .that()
        .resideInAPackage("com.bone.metadata..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("com.bone.app..", "com.bone.accounting..")
        .because(
            "bone-metadata-server 是纯平台，业务代码必须住在 bone-app-* / bone-accounting-engine，"
                + "平台不得反向依赖业务模块（否则即把应用逻辑混入引擎）")
        .check(classes());
  }

  @Test
  void accountingEngineMustNotDependOnBusinessVerticals() {
    noClasses()
        .that()
        .resideInAPackage("com.bone.accounting..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.bone.app..")
        .because(
            "会计引擎是横向可复用能力，不得依赖具体业务垂直（报销/采购）；复用经 fin_voucher_template"
                + " 与动作定义 handler_bean 契约，不靠编译依赖")
        .check(classes());
  }

  @Test
  void businessAppsMustNotDependOnAccountingEngineAtCompileTime() {
    noClasses()
        .that()
        .resideInAPackage("com.bone.app..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.bone.accounting..")
        .because(
            "业务应用与会计引擎解耦：应用只在动作定义声明 handler_bean=voucherGenerateHandler，"
                + "运行期由容器装配；编译期依赖会让引擎复用退化为硬编码")
        .check(classes());
  }
}
