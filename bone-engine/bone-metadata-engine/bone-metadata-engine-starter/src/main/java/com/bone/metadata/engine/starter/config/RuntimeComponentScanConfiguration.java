package com.bone.metadata.engine.starter.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * 引擎 runtime 包的组件注册（2026-10-07 新增，承接原自动配置类上的 {@code @ComponentScan}）。
 *
 * <p><b>为什么要拆出来</b>：Spring Boot 官方对自动配置类的要求里明确写着 「<b>auto-configuration classes should not enable
 * component scanning to find additional components. Specific {@code @Imports} should be used
 * instead.</b>」 （reference「Creating Your Own Auto-configuration」）。
 *
 * <p>实测依据（本轮，2026-10-07）：
 *
 * <ul>
 *   <li>runtime 下共<b>27 个</b> {@code @Component/@Service/@Repository} 类， <b>全部</b>位于 {@code
 *       com.bone.metadata.**} 包下；
 *   <li>而唯一真实的宿主 {@code bone-metadata-server} 的 {@code @SpringBootApplication} 扫的正是 {@code
 *       com.bone.metadata.**} ⇒ <b>这 27 个类宿主自己就会扫到</b>，starter 再扫一遍属于<b>重复扫描</b>；
 *   <li>实测把自动配置类上的 {@code @ComponentScan} 整个去掉后， {@code ServerContextLoadsTest}
 *       仍然<b>通过</b>，证实了上述判断。
 * </ul>
 *
 * <p><b>为什么仍要保留这个类（而不是彻底删掉扫描）</b>：本 starter 是**可独立使用**的 自动化配置 —— 若某个下游服务没有把 {@code
 * com.bone.metadata.**} 纳入自己的扫描范围， 直接依赖 starter 就得不到这 27 个 bean（实测会报 {@code
 * NoSuchBeanDefinitionException}）。 所以扫描职责被<b>显式化</b>到本类：自动配置类保持"干净"（符合官方要求）， 而"要不要让 starter
 * 自带扫描"变成一个<b>可讨论、可关闭</b>的独立决策。
 *
 * <p><b>排除项为何必须留在这里</b>：这些类都<b>依赖宿主基础设施</b>（SDK Repository / DataSource），starter
 * 替它们装配只会启动失败；且它们都有各自的宿主侧实现或 Noop 兜底。
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(
    basePackages = "com.bone.metadata.engine.runtime",
    excludeFilters = {
      // ① SDK 自身的仓储配置：它需要宿主提供 DataSource，starter 不该替它装配
      @Filter(
          type = FilterType.ASSIGNABLE_TYPE,
          classes =
              com.bone.metadata.engine.runtime.adapter.config.EngineSdkRepositoryConfig.class),
      // ② 【2026-10-07 已移除】原先在此排除 runtime 的 RuntimeEngineAutoConfiguration。
      //    排除理由是它也定义了 ports.registry.MetadataRegistry（bean 名 portsMetadataRegistry），
      //    与 starter 曾重复定义的 registryMetadataRegistry() 冲突。
      //    ★ 那份重复定义已删除（改为单一事实来源），故**不能再排除它**——
      //    否则 starter 单独装配时没有任何注册中心，metadataEngine 注入不到
      //    ports.registry.MetadataRegistry 而启动失败。该类自带 @ConditionalOnMissingBean。
      // ③ domain 层的 DefaultMetadataRegistry：它实现【domain.metadata.MetadataRegistry】
      //    这个**另一个同名接口**，会被按类型扫描误当成 ports 版注册中心。
      //    注册中心统一由 RuntimeEngineAutoConfiguration 提供。
      @Filter(
          type = FilterType.ASSIGNABLE_TYPE,
          classes = com.bone.metadata.engine.domain.metadata.DefaultMetadataRegistry.class),
      // ④ 整个 runtime.adapter 包整体排除：SdkMetadataRepository / IamMetadataBridge 的
      //    构造器都要注入 bone-metadata-sdk 的 Repository<MetaEntityPo, Long> —— 那是**宿主**
      //    通过 @EnableSqlRepositories 注册的 bean，starter 单独跑时不存在
      //    ⇒ UnsatisfiedDependencyException。
      //    ★ 用【包级正则】而非逐个类排除：逐个类只挡住现有的两个，
      //    未来该包再加宿主相关实现就会重蹈覆辙。
      //    语义上也更正确 —— adapter 包本就是"依赖宿主基础设施"的适配层；
      //    默认走 NoopMetadataPlatformBridge（IamMetadataBridge 的 javadoc 明确写了这点）。
      @Filter(
          type = FilterType.REGEX,
          pattern = "com\\.bone\\.metadata\\.engine\\.runtime\\.adapter\\..*")
    })
public class RuntimeComponentScanConfiguration {}
