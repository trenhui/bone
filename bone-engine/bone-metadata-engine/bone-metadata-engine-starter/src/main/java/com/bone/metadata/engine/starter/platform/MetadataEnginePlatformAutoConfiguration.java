package com.bone.metadata.engine.starter.platform;

import com.bone.metadata.engine.ports.spi.MetadataPlatformBridge;
import com.bone.metadata.engine.ports.spi.NoopMetadataPlatformBridge;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** META-ENG-01：注册平台元数据桥接 Bean（SDK 可用时用 SdkMetadataPlatformBridge）。 */
@AutoConfiguration
public class MetadataEnginePlatformAutoConfiguration {

  /**
   * SDK 桥接【移入嵌套配置类】（2026-10-07 修正）。
   *
   * <p><b>原写法的问题</b>：{@code @ConditionalOnClass(QueryBuilder.class)} 直接打在 {@code @Bean} 方法上。 Spring
   * Boot 官方文档（reference「Developing Your Own Auto-configuration」· Class Conditions） 明确写道：该机制「does
   * not apply the same way to {@code @Bean} methods where typically <b>the return type is the
   * target of the condition</b>」⇒ 条件按方法返回类型 （{@code MetadataPlatformBridge}，本仓库自有接口，恒在
   * classpath）判断， <b>而不是</b>按 {@code QueryBuilder} 判断 ⇒ <b>条件形同虚设</b>。
   *
   * <p><b>为何必须修</b>：本starter 把 bone-metadata-sdk 声明为<b>非可选</b>依赖， 所以今天 {@code QueryBuilder} 必然在
   * classpath 上、结果"碰巧是对的"； 但一旦下游把 SDK 改为 {@code <optional>true</optional>}（使用 starter 却不用 SDK
   * 查询能力）， 这个条件<b>不会</b>按预期回退到 {@link NoopMetadataPlatformBridge}， 而是会在解析 {@code
   * SdkMetadataPlatformBridge} 时抛 {@code NoClassDefFoundError}。 也就是说：<b>它是一颗定时炸弹，只在有人把 SDK
   * 改成可选时才会炸</b>。
   *
   * <p>移入嵌套类后，条件在<b>类级</b>判断（此时 JVM 尚未加载下方 {@code @Bean} 方法的签名）， 这才是官方指定的生效位置。契约见 {@code
   * MetadataEngineAutoConfigurationWiringTest}。
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(QueryBuilder.class)
  static class SdkBridgeConfiguration {

    /**
     * SDK 版桥接。
     *
     * <p>★ 注意这里<b>只</b>用 {@code @ConditionalOnMissingBean}、不再重复写 {@code @ConditionalOnClass}——
     * 类级已经保证了 SDK 在场。
     */
    @Bean
    @ConditionalOnMissingBean(MetadataPlatformBridge.class)
    MetadataPlatformBridge sdkMetadataPlatformBridge() {
      return new SdkMetadataPlatformBridge();
    }
  }

  /**
   * Noop 版桥接（兜底）。
   *
   * <p><b>为什么它不需要 {@code @ConditionalOnClass(QueryBuilder.class)}</b>： 它依赖的是 {@link
   * NoopMetadataPlatformBridge}（本仓库自有，不碰 SDK）， 故无论 SDK 在不在都能创建。当 SDK 在场时，SDK 版因
   * {@code @ConditionalOnMissingBean} 先注册而胜出（自动配置类后于用户配置处理， 这是 {@code @ConditionalOnMissingBean}
   * 生效的前提）；SDK 不在场时只剩它兜底。
   */
  @Bean
  @ConditionalOnMissingBean(MetadataPlatformBridge.class)
  MetadataPlatformBridge noopMetadataPlatformBridge() {
    return new NoopMetadataPlatformBridge();
  }
}
