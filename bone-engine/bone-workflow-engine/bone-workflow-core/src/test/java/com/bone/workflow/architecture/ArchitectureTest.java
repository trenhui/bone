package com.bone.workflow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 审批流引擎内核（bone-workflow-core）架构守护。
 *
 * <p>core 由 domain / ports / runtime 三个包合成<b>一个模块</b>（业界 engine + starter 两制品形态，见设计文档 §2）。
 * 模块合并不等于层次消失——以下规则按<b>包</b>继续守护依赖方向， 与拆分模块时等价：
 *
 * <ol>
 *   <li>domain 必须是纯逻辑：不依赖 Spring、不依赖平台元数据引擎、不依赖 ports/runtime/starter；
 *   <li>ports 只能依赖 domain（SPI 契约引用领域模型），不得反向依赖 runtime/starter；
 *   <li>runtime 不得依赖平台元数据引擎——引擎与平台之间只允许经 ports 契约对话。
 * </ol>
 *
 * <p>为什么值得守：某天有人在 domain 里 @Autowired 一个平台 bean，或在 runtime 里直连 {@code
 * bone-metadata-engine}，引擎就退化成「只能在平台里跑的一段代码」，失去被采购、报销等场景复用的能力—— 而且这种退化编译期不报错、运行期在平台外才爆炸。
 */
@AnalyzeClasses(
    packages = "com.bone.workflow",
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

  @ArchTest
  static final ArchRule ports_must_only_depend_on_domain =
      noClasses()
          .that()
          .resideInAPackage("com.bone.workflow.ports..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.bone.workflow.runtime..",
              "com.bone.workflow.starter..",
              "org.springframework..",
              "com.bone.metadata..")
          .because("ports 是引擎对外的 SPI 契约：只引用 domain 模型，不依赖编排层与平台（否则 SPI 被实现细节绑架）");

  @ArchTest
  static final ArchRule runtime_must_not_depend_on_platform_metadata_engine =
      noClasses()
          .that()
          .resideInAPackage("com.bone.workflow.runtime..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.bone.metadata..", "com.bone.metadata.sdk..")
          .because("引擎与平台只经 ports 契约对话，不得直连平台元数据引擎（否则无法被其它场景复用）");

  /** core 内部依赖方向自证：domain ← ports ← runtime（domain 不被任何内层依赖，只可被外层依赖）。 */
  @ArchTest
  static final ArchRule core_layers_are_ordered =
      classes()
          .that()
          .resideInAPackage("com.bone.workflow.domain..")
          .should()
          .onlyBeAccessed()
          .byAnyPackage(
              "com.bone.workflow.domain..",
              "com.bone.workflow.ports..",
              "com.bone.workflow.runtime..")
          .because("core 内依赖方向只能是 domain ← ports ← runtime；反向访问说明层次被击穿");
}
