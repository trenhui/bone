package com.bone.workflow.starter.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 审批流引擎<b>跨模块</b>架构守护（唯一能看到 core + starter 两个制品的视角）。
 *
 * <p><b>为什么必须放在 starter 而不是 core</b>：四模块时代的守护是「模块间」物理隔离——{@code bone-workflow-domain} 的 编译期就不可能引用
 * {@code bone-workflow-starter} 的类。收敛为两模块后，物理隔离降级为「包间」约定，而 <b>core 的 ArchUnit 测试看不见 starter
 * 的类</b>（starter 依赖 core，反向不成立）⇒ 一条写在 {@code com.bone.workflow.domain} 包下、却住在 starter 模块里的类，可以自由依赖
 * Spring / 平台而无人发现：core 自测看不见它，starter 此前没有任何守护。
 *
 * <p>本类是唯一的修复点——starter 的测试 classpath 上同时存在 core 与 starter 两边的类，因此可以断言 <b>整个 {@code
 * com.bone.workflow} 命名空间</b>的纯度，而不只是 core 内部。
 *
 * <p>三条纪律：
 *
 * <ol>
 *   <li>引擎命名空间（{@code domain} / {@code ports} / {@code runtime}）内<b>任何</b>类都不得依赖 Spring / 平台元数据 /
 *       {@code bone-core}——不论它住在哪个模块（这条同时兜住「包占位」：starter 里放一个 domain 包的 Spring 类会被这条抓住）；
 *   <li>引擎命名空间不得反向依赖 {@code com.bone.workflow.starter}——适配器层是引擎的<b>下游</b>，反向引用即环形依赖；
 *   <li>本模块不重复 core 内部的层序规则（{@code domain ← ports ← runtime}）：那由 core 自己的 {@code ArchitectureTest}
 *       负责，且 starter <b>合法地</b>访问 {@code domain} 模型，若把「domain 只许被 domain/ports/runtime 访问」搬到本类会误报。
 * </ol>
 */
@AnalyzeClasses(
    packages = "com.bone.workflow",
    importOptions = ImportOption.DoNotIncludeTests.class)
class StarterArchitectureTest {

  /**
   * 引擎命名空间不得依赖框架 / 平台。{@code com.bone.workflow.starter..} 也一并禁止：引擎不认识「适配器层」这个概念。
   *
   * <p>之所以要跨模块断言：core 的守护只覆盖 core 自己的类，starter 里冒出来的引擎包名类会被漏掉。
   */
  @ArchTest
  static final ArchRule engine_namespace_must_not_depend_on_framework_or_platform =
      noClasses()
          .that()
          .resideInAnyPackage(
              "com.bone.workflow.domain..",
              "com.bone.workflow.ports..",
              "com.bone.workflow.runtime..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "com.bone.metadata..",
              "com.bone.core..",
              "com.bone.workflow.starter..")
          .because(
              "引擎内核（domain/ports/runtime）须为纯 Java：不依赖 Spring、不依赖平台元数据引擎、不依赖 bone-core、"
                  + "也不反向依赖适配器层——否则「引擎可被任意宿主独立复用」的能力静默消失，"
                  + "且这种退化编译期不报错、只在脱离平台运行时才爆炸");

  /**
   * 引擎命名空间内的类<b>只许</b>依赖 JDK 与本命名空间（Jackson / SLF4J 属引擎自身的数据绑定与日志设施，不在禁止之列）。
   *
   * <p>这是上一条的<b>补漏</b>：上一条只列了「已知的坏依赖」，本条从白名单侧封死——任何新引入的第三方框架（Guava、Aviator、 某个 Spring
   * starter）都会因为不在白名单而变红，避免「加了个新依赖忘了加进禁止清单」这种漏网。
   */
  @ArchTest
  static final ArchRule engine_namespace_must_not_depend_on_unexpected_third_party =
      noClasses()
          .that()
          .resideInAnyPackage(
              "com.bone.workflow.domain..",
              "com.bone.workflow.ports..",
              "com.bone.workflow.runtime..")
          .should()
          .dependOnClassesThat()
          .resideOutsideOfPackages(
              "java..",
              "javax..",
              "com.fasterxml.jackson..",
              "org.slf4j..",
              "com.bone.workflow.domain..",
              "com.bone.workflow.ports..",
              "com.bone.workflow.runtime..")
          .because(
              "引擎内核只允许依赖 JDK + Jackson（数据绑定）+ SLF4J（日志）；引入其它第三方库会同时污染两个制品的传递依赖，"
                  + "让「纯 Java 制品」这一承诺失效");
}
