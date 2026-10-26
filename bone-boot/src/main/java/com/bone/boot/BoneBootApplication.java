package com.bone.boot;

import com.bone.metadata.MetadataApplication;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 运行时组合根（composition root）。
 *
 * <p>本模块不包含任何业务/平台逻辑，仅负责<strong>部署期装配</strong>：复用 {@link MetadataApplication} 的启动配置（组件扫描根 {@code
 * com.bone.metadata} + Spring Boot 自动配置加载），并把各业务应用模块 （bone-app-expense / bone-accounting-engine /
 * bone-app-purchase）以自动配置形式挂入 classpath。
 *
 * <p>由此保证：bone-metadata-server 作为纯平台，<strong>不反向依赖</strong>任何业务模块；业务能力随 jar 挂载而生效。
 */
@Configuration(proxyBeanMethods = false)
public class BoneBootApplication {

  public static void main(String[] args) {
    // 两个主配置源：MetadataApplication 承载平台扫描/注解，本类承载组合根专属装配。
    // 本类不在 MetadataApplication 的扫描根（com.bone.metadata）内，必须作为主源显式传入。
    SpringApplication.run(
        new Class<?>[] {MetadataApplication.class, BoneBootApplication.class}, args);
  }

  /**
   * 组合根显式提供 <b>@Primary Executor</b>（虚拟线程，与 {@code
   * MetadataEngineAutoConfiguration#metadataValidationExecutor} 同语义）。
   *
   * <p><b>为什么必须在组合根补这个 bean（2026-10-26 联调实测确诊）</b>：{@code StrategyValidationExecutor} 构造器注入 裸
   * {@link Executor}。standalone bone-metadata-server 的上下文里 Boot 只创建了 {@code
   * applicationTaskExecutor} 单候选，解析顺利；bone-boot 组合根的自动配置排序不同，{@code
   * TaskSchedulingConfigurations$TaskSchedulerConfiguration} 也被装配 ⇒ 候选变成 {@code
   * applicationTaskExecutor} + {@code taskScheduler} 两个，注入直接
   * NoUniqueBeanDefinitionException，应用起不来（starter 侧 {@code metadataValidationExecutor} 带
   * {@code @ConditionalOnMissingBean(Executor.class)}，因 Boot 先注册而退让，救不了场）。组合根统一指定 @Primary 后：注入按主
   * bean 解析，两个 Boot 任务 bean 保留给各自用途（@Async / @Scheduled 不受影响）。
   */
  @Bean
  @Primary
  public Executor compositionRootValidationExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }
}
