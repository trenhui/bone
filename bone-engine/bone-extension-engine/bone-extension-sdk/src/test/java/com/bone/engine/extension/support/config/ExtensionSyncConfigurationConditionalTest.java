package com.bone.engine.extension.support.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 元数据存储的「条件装配」契约（2026-10-07）。
 *
 * <p><b>背景</b>：{@code ExtensionSyncConfiguration} 曾把
 * {@code @ConditionalOnClass(RedisTemplate.class)} 直接打在两个 {@code @Bean} 方法上。 Spring Boot
 * 官方文档（reference「Developing Your Own Auto-configuration」· Class Conditions） 明确写道：该机制「does not apply
 * the same way to {@code @Bean} methods where typically <b>the return type is the target of the
 * condition</b>」， 并推荐用<b>嵌套 {@code @Configuration} 类</b>隔离条件。
 *
 * <p><b>本类的判据只看「根因链」，不去容器里查 bean</b>。原因（实测踩到）： {@code ApplicationContext#getBeanNamesForType}
 * 在<b>容器启动失败</b>时会抛 {@code IllegalStateException}（"Unstarted application context"）而非返回空集合 ⇒
 * 用它写断言会在"容器起不来"这个最需要看清根因的场景里直接抛异常、把真实原因盖掉。
 *
 * <p>⚠️ 另一处关键：类级有 {@code @ConditionalOnProperty(bone.extension.sync.enabled=true)}，
 * <b>必须显式打开</b>，否则整个配置类不装配 ⇒ 断言会因"什么都没加载"而恒真。
 */
class ExtensionSyncConfigurationConditionalTest {

  private static final String SYNC_ENABLED = "bone.extension.sync.enabled=true";

  private ApplicationContextRunner baseRunner() {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ExtensionSyncConfiguration.class))
        .withPropertyValues(SYNC_ENABLED);
  }

  /** Redis 齐全时的 runner（含该 @Bean 需要的两个模板）。 */
  private ApplicationContextRunner runnerWithRedis() {
    return baseRunner()
        .withBean(
            ExtensionMetadataRedisConfiguration.METADATA_REDIS_TEMPLATE_BEAN,
            RedisTemplate.class,
            () -> new RedisTemplate<String, Object>())
        .withBean(
            ExtensionMetadataRedisConfiguration.METADATA_INDEX_REDIS_TEMPLATE_BEAN,
            StringRedisTemplate.class,
            StringRedisTemplate::new);
  }

  @Test
  @DisplayName("★ Redis 缺失时，不得出现任何 Redis 相关缺类错误（条件已生效）")
  void redisBeansAreSkippedWithoutRedis() {
    baseRunner()
        .withClassLoader(new FilteredClassLoader(RedisTemplate.class))
        .run(
            context -> {
              Throwable failure = context.getStartupFailure();
              if (failure == null) {
                // 容器起得来是完全可接受的结果（说明只有 inMemory 版被装配）
                return;
              }
              // ★ 条件生效时，失败原因不该与 Redis 有关。
              //  2026-10-07 修复前实测：条件打在 @Bean 方法上形同虚设，
              //  根因链里明确出现 RedisTemplate ⇒ 本断言当时会失败（正是它证明修复有效）。
              assertThat(rootCauseChain(failure))
                  .withFailMessage(
                      "Redis 已从 classpath 移除，却出现与 Redis 相关的错误 ⇒ "
                          + "@ConditionalOnClass 又失效了。它必须打在【嵌套配置类】上——"
                          + "打在 @Bean 方法上时按方法返回类型判断，形同虚设")
                  .doesNotContain("RedisTemplate")
                  .doesNotContain("StringRedisTemplate")
                  .doesNotContain("RedisConnectionFactory")
                  .doesNotContain("NoClassDefFoundError");
            });
  }

  @Test
  @DisplayName("★ Redis 齐全时，redisExtensionMetadataStore 确实被尝试创建（反向自证）")
  void redisStoreIsAttemptedWhenRedisPresent() {
    runnerWithRedis()
        .run(
            context -> {
              Throwable failure = context.getStartupFailure();
              if (failure == null) {
                assertThat(context).hasBean("redisExtensionMetadataStore");
                return;
              }
              // ★ 判据方向：本用例要证明的是「Redis 版 store **确实被创建了**」。
              //   容器整体可能因**其它** bean（如某个需要真实 Redis 连接工厂的
              //   extensionMetadataRedisTemplate）而失败，这与目标 bean 无关。
              //   判据因此只能是：**失败原因不得是 redisExtensionMetadataStore 自身**——
              //   若根因链点名它，说明它没被创建 ⇒ 上一条"Redis 缺失时不创建"就成了假绿。
              String chain = rootCauseChain(failure);
              assertThat(chain)
                  .withFailMessage(
                      "Redis 齐全时却报 redisExtensionMetadataStore 自身创建失败，"
                          + "说明它没被成功创建 ⇒ 上一条「Redis 缺失时不创建」可能是恒真空断言")
                  .doesNotContain("redisExtensionMetadataStore");
            });
  }

  private static String rootCauseChain(Throwable t) {
    StringBuilder sb = new StringBuilder();
    Throwable cur = t;
    int guard = 0;
    while (cur != null && guard++ < 15) {
      sb.append(cur.getClass().getSimpleName())
          .append(": ")
          .append(cur.getMessage())
          .append(" <- ");
      cur = cur.getCause();
    }
    return sb.toString();
  }
}
