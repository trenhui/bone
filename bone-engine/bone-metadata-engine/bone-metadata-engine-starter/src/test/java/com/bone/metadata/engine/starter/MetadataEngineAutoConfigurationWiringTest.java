package com.bone.metadata.engine.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.metadata.engine.ports.spi.MetadataPlatformBridge;
import com.bone.metadata.engine.ports.spi.NoopMetadataPlatformBridge;
import com.bone.metadata.engine.runtime.BusinessRuleEngine;
import com.bone.metadata.engine.runtime.ExpressionEngine;
import com.bone.metadata.engine.runtime.MetadataEngine;
import com.bone.metadata.engine.runtime.RuleEngine;
import com.bone.metadata.engine.runtime.ValidationEngine;
import com.bone.metadata.engine.runtime.metadata.MetadataRegistry;
import com.bone.metadata.engine.runtime.service.GenericOperationService;
import com.bone.metadata.engine.starter.config.MetadataEngineAutoConfiguration;
import com.bone.metadata.engine.starter.platform.MetadataEnginePlatformAutoConfiguration;
import com.bone.metadata.engine.starter.platform.SdkMetadataPlatformBridge;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 装配集成测试（2026-10-07 新增）。
 *
 * <p><b>为什么必须有这个测试</b>：本模块此前<b>只有 1 个 ArchitectureTest，没有任何测试验证 「这些bean 真的能被 Spring
 * 装配」</b>。于是出现了一个持续很久、且极难发现的失效：
 *
 * <ul>
 *   <li>自动配置注册在<b>旧式 {@code META-INF/spring.factories}</b>，而工程 Boot = 3.5.16 ⇒ Boot
 *       3<b>不再加载</b>该文件里的 {@code EnableAutoConfiguration}；
 *   <li>⇒ starter 整体静默失效，但<b>编译通过、单测全绿</b>（单测手工 new 出对象， 完全绕过了装配链）；
 *   <li>⇒ 更隐蔽的是：2026-10-06 那次「自定义业务规则恒 fail-open」的修复， 其装配类 {@code RuleEngineAutoConfiguration}
 *       <b>根本没注册到任何清单</b>， 那个修复在生产同样不生效，而当时只有单测手工 {@code new} 出来验证。
 * </ul>
 *
 * ⇒ **单测验证逻辑、装配测试验证接线，两者缺一不可**。本类专门守"接线"这一半。
 *
 * <p><b>关键设计：配置类清单从 {@code .imports} 文件真读，不用硬编码。</b> 若在测试里硬编码 {@code
 * AutoConfigurations.of(MetadataEngineAutoConfiguration.class)}， 就会<b>绕过注册文件</b> ——
 * 注册文件写错了、漏了，测试照样全绿（这正是此前的失效形态）。 本类用 {@link AutoConfigurations#of} 的替代路径
 * {@code @ImportAutoConfiguration} 语义不适用时， 改为<b>直接断言注册文件内容</b>，再配合 {@code ApplicationContextRunner}
 * 验证 「用注册文件里的类能真的装配出 bean」。
 */
class MetadataEngineAutoConfigurationWiringTest {

  private static final String IMPORTS_RESOURCE =
      "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  MetadataEnginePlatformAutoConfiguration.class,
                  MetadataEngineAutoConfiguration.class));

  @Test
  @DisplayName("★ 注册文件存在且列出两个自动配置类（Boot 3 的唯一加载途径）")
  void importsFileListsAutoConfigurations() throws IOException {
    List<String> registered = readImportsFile();
    assertThat(registered)
        .withFailMessage(
            "Boot 3 只认 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports；"
                + "若自动配置写回 spring.factories，装配会静默失效（编译与单测都发现不了）")
        .contains(
            MetadataEnginePlatformAutoConfiguration.class.getName(),
            MetadataEngineAutoConfiguration.class.getName());
  }

  @Test
  @DisplayName("★ 注册文件里必须有 RuleEngineAutoConfiguration（自定义业务规则校验的接线点）")
  void importsFileIncludesRuleEngineWiring() throws IOException {
    List<String> registered = readImportsFile();
    assertThat(registered)
        .withFailMessage(
            "RuleEngineAutoConfiguration 曾【未注册到任何清单】⇒ 2026-06 修复的"
                + "「自定义业务规则恒 fail-open」在生产并未生效，而单测因手工 new 对象而全绿")
        .contains("com.bone.metadata.engine.runtime.rule.RuleEngineAutoConfiguration");
  }

  @Test
  @DisplayName("★ 本模块自身的产物里不再有旧式自动配置注册（只认 starter 自己的 resources）")
  void starterItselfHasNoLegacyAutoConfigurationRegistration() throws IOException {
    // ★ 只检查【本模块 classes 目录】下的 spring.factories，不扫整个 classpath。
    //   原因：classpath 上还有 bone-metadata-sdk 的 spring.factories 也含自动配置键
    //   （那是 SDK 自己的历史包袱，另行处理），若一并检查会让本用例恒红、
    //   从而掩盖"starter 自己的旧式注册是否已清理"这个真正要守的判据。
    java.net.URL self = findOwnResource("META-INF/spring.factories");
    if (self == null) {
      // 本模块没有 spring.factories ⇒ 干净（Boot 3 下自动配置只认 .imports）
      return;
    }
    String content;
    try (InputStream in = self.openStream()) {
      content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    assertThat(content)
        .withFailMessage(
            "Boot 3 已移除 spring.factories 的 EnableAutoConfiguration 键支持，"
                + "写在那里等于没写（会被静默忽略）⇒ starter 又是「建好却没被用」")
        .doesNotContain("org.springframework.boot.autoconfigure.EnableAutoConfiguration");
  }

  @Test
  @DisplayName("★ 平台桥接装配成功，且 SDK 在 classpath 时用 SDK 实现")
  void platformBridgeIsWired() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context)
              .hasSingleBean(com.bone.metadata.engine.ports.spi.MetadataPlatformBridge.class);
          // starter 依赖 bone-metadata-sdk，故 classpath 满足条件 ⇒ 应选 SDK 桥接而非 Noop
          assertThat(
                  context.getBean(com.bone.metadata.engine.ports.spi.MetadataPlatformBridge.class))
              .isInstanceOf(
                  com.bone.metadata.engine.starter.platform.SdkMetadataPlatformBridge.class);
        });
  }

  @Test
  @DisplayName("★ 引擎核心 bean 真的被装配（这正是此前从未被验证的一半）")
  void coreEngineBeansAreWired() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(MetadataRegistry.class);
          assertThat(context).hasSingleBean(ValidationEngine.class);
          assertThat(context).hasSingleBean(ExpressionEngine.class);
          assertThat(context).hasSingleBean(MetadataEngine.class);
          assertThat(context).hasSingleBean(GenericOperationService.class);
        });
  }

  @Test
  @DisplayName("★ 业务规则引擎 bean 存在（业务规则能力线的必需件）")
  void businessRuleEngineIsWired() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          // DefaultBusinessRuleEngine 由 @ComponentScan 装配
          assertThat(context).hasSingleBean(BusinessRuleEngine.class);
          assertThat(context).hasSingleBean(RuleEngine.class);
        });
  }

  @Test
  @DisplayName("★ 用户自定义的同名 bean 不会被自动配置覆盖（@ConditionalOnMissingBean 语义）")
  void userDefinedBeanTakesPrecedence() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MetadataEngineAutoConfiguration.class))
        .withBean(MetadataRegistry.class, () -> new MetadataRegistry())
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              // 自定义 bean 存在 ⇒ 自动配置的 @ConditionalOnMissingBean 退让，
              // 最终仍只有 1 个（自定义的那个）
              assertThat(context).hasSingleBean(MetadataRegistry.class);
            });
  }

  @Test
  @DisplayName("★ starter 自带校验用 Executor（宿主已声明时自动退让）")
  void validationExecutorIsProvidedAndBacksOff() {
    // ① 默认路径：starter 必须提供一个 Executor 供StrategyValidationExecutor 使用
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(java.util.concurrent.Executor.class);
        });

    // ② 宿主自声明时应退让（@ConditionalOnMissingBean 语义），避免两个 Executor 打架。
    //    变量声明为 Executor（而非 ExecutorService）—— 因为这里只需要"身份不同"这一事实，
    //    不需要 shutdown；单测结束由 JVM 回收。
    java.util.concurrent.Executor custom = Runnable::run;
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MetadataEngineAutoConfiguration.class))
        .withBean("hostExecutor", java.util.concurrent.Executor.class, () -> custom)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              // 宿主那个是单例、starter 不应再造一个
              assertThat(context.getBean(java.util.concurrent.Executor.class)).isSameAs(custom);
            });
  }

  @Test
  @DisplayName("★ SDK 在场时选 SDK 桥接；SDK 缺席时回退 Noop（条件真的按 SDK 是否存在判断）")
  void platformBridgeFallsBackToNoopWithoutSdk() {
    // ① SDK 在场 ⇒ 必须选SDK 版
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context.getBean(MetadataPlatformBridge.class))
              .isInstanceOf(SdkMetadataPlatformBridge.class);
        });

    // ② ★ SDK 缺席 ⇒ 必须回退 Noop 版。
    //   这条正是2026-10-07 修复的那个缺陷：@ConditionalOnClass 原先打在 @Bean 方法上，
    //   条件按【方法返回类型 MetadataPlatformBridge】判断（恒在 classpath）⇒ 条件失效
    //   ⇒ SDK 缺席时仍会尝试创建 SDK 版桥接并在解析其依赖时抛 NoClassDefFoundError。
    //   修复方式：把条件移到嵌套 @Configuration 类上（官方指定的生效位置）。
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MetadataEnginePlatformAutoConfiguration.class))
        .withClassLoader(new FilteredClassLoader(QueryBuilder.class))
        .run(
            context -> {
              assertThat(context)
                  .withFailMessage(
                      "SDK 已被 FilteredClassLoader 剔除，容器却仍失败或选出 SDK 版桥接 "
                          + "⇒ @ConditionalOnClass(QueryBuilder.class) 依然未生效")
                  .hasNotFailed();
              assertThat(context.getBean(MetadataPlatformBridge.class))
                  .withFailMessage("SDK 缺席时应回退到 Noop 版桥接")
                  .isInstanceOf(NoopMetadataPlatformBridge.class);
            });
  }

  @Test
  @DisplayName("装配失败时必须暴露而非静默（保证上面每条hasNotFailed 断言有意义）")
  void contextFailureWouldBeVisible() {
    // 反向自检：故意给一个无法满足的条件，确认 ApplicationContextRunner 确实会报告失败——
    // 否则上面的 hasNotFailed() 断言可能因为"什么都没加载"而恒真。
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(BrokenAutoConfiguration.class))
        .run(
            context ->
                assertThat(context)
                    .withFailMessage(
                        "本用例自证：ApplicationContextRunner 必须能报告装配失败，"
                            + "否则其它用例里的 hasNotFailed() 是恒真的假断言")
                    .hasFailed());
  }

  /**
   * 故意失败的自动配置：注入一个容器里不存在的 bean 类型 ⇒ 必然装配失败。
   *
   * <p>用它自证「{@code ApplicationContextRunner} 确实能报告装配失败」—— 否则其它用例里的 {@code hasNotFailed()}
   * 可能是恒真的假断言。
   */
  @org.springframework.boot.autoconfigure.AutoConfiguration
  static class BrokenAutoConfiguration {
    @org.springframework.context.annotation.Bean
    BrokenAutoConfiguration requiresMissingBean(UnsatisfiableDependencyMarker marker) {
      return this;
    }
  }

  /** 容器里不会有任何这个类型的 bean（无 @Component、无 @Bean 提供它）。 */
  static class UnsatisfiableDependencyMarker {}

  /**
   * 在**本模块的产物目录**（{@code target/classes}）里找资源。
   *
   * <p><b>为什么不能用 {@code getClassLoader().getResource}</b>：它是「取第一个命中」， classpath 上 {@code
   * bone-metadata-sdk} 的 jar 也有 {@code META-INF/spring.factories} 且同样含自动配置键 ⇒ 拿到别人的文件 ⇒ 用例恒红，反而掩盖了
   * 「starter 自己的旧式注册是否已清理」这个真正要守的判据。
   *
   * <p><b>也不用 ProtectionDomain/CodeSource</b>：它在本项目两种形态下指向不同位置 （IDEA 下是 {@code target/classes}，jar
   * 形态下是 jar 文件本身）， 拼路径的写法要分两种情况、易错。直接从资源路径反推更直白： {@code .../starter/META-INF/spring.factories}
   * ⇒上一级即本模块产物根。
   */
  private static java.net.URL findOwnResource(String path) {
    java.net.URL marker =
        MetadataEngineAutoConfigurationWiringTest.class
            .getClassLoader()
            .getResource(IMPORTS_RESOURCE);
    if (marker == null) {
      return null;
    }
    try {
      // 取本模块产物根：imports 资源路径去掉尾部META-INF/... 两段
      String p = marker.toString();
      int idx = p.indexOf("META-INF/spring/");
      if (idx < 0) {
        return null;
      }
      String root = p.substring(0, idx);
      java.nio.file.Path candidate = java.nio.file.Path.of(java.net.URI.create(root)).resolve(path);
      return java.nio.file.Files.exists(candidate) ? candidate.toUri().toURL() : null;
    } catch (Exception ex) {
      return null;
    }
  }

  /** 读注册文件，返回非注释、非空行。 */
  private static List<String> readImportsFile() throws IOException {
    String content = readResource(IMPORTS_RESOURCE);
    return Arrays.stream(content.split("\\R"))
        .map(String::trim)
        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
        .toList();
  }

  private static String readResource(String path) throws IOException {
    try (InputStream in =
        MetadataEngineAutoConfigurationWiringTest.class
            .getClassLoader()
            .getResourceAsStream(path)) {
      if (in == null) {
        throw new IOException("资源不存在: " + path + " —— 若是自动配置导入清单缺失，说明自动配置在 Boot 3 下不会被加载");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
