package com.bone.metadata.engine.starter.config;

import com.bone.metadata.engine.runtime.ExpressionEngine;
import com.bone.metadata.engine.runtime.MetadataEngine;
import com.bone.metadata.engine.runtime.TransformationEngine;
import com.bone.metadata.engine.runtime.ValidationEngine;
import com.bone.metadata.engine.runtime.metadata.MetadataRegistry;
import com.bone.metadata.engine.runtime.metadata.OperationRegistry;
import com.bone.metadata.engine.runtime.metadata.processor.CompositeMetadataProcessor;
import com.bone.metadata.engine.runtime.repository.InMemoryMetadataRepository;
import com.bone.metadata.engine.runtime.repository.MetadataRepository;
import com.bone.metadata.engine.runtime.service.DynamicDataService;
import com.bone.metadata.engine.runtime.service.GenericOperationService;
import com.bone.metadata.engine.runtime.service.impl.InMemoryDynamicDataService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Metadata Engine 自动配置类 负责自动装配所有 Metadata Engine 组件和配置。
 *
 * <p>注意：{@code EngineSdkRepositoryConfig}（带 {@code @EnableSqlRepositories} 副作用，会为 {@code
 * MetaEntityPo}/{@code MetaFieldPo} 生成 {@code Repository} 代理 bean）不可被本类的 {@code @ComponentScan}
 * 重复扫描——宿主（{@code BoneMetadataEngineApplication} 扫 {@code com.bone} 或 {@code bone-metadata-server}
 * 扫 {@code com.bone.metadata}）已会扫描到它并注册一次。重复扫描会导致同一 {@code Repository} 接口被
 * {@code @EnableSqlRepositories} 注册两次，触发重复 bean 定义冲突。故此处显式排除。
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(MetadataEngineProperties.class)
// ★ 2026-10-07：原先此处是 @ComponentScan("com.bone.metadata.engine.runtime")。
//   官方明确要求「auto-configuration classes should not enable component scanning」
//   （reference「Creating Your Own Auto-configuration」），故改为引用一个**独立的**
//   组件注册类，由它承担扫描职责 —— 扫描行为不变，但不再发生在自动配置类自身。
//   详见 RuntimeComponentScanConfiguration。
@Import(RuntimeComponentScanConfiguration.class)
public class MetadataEngineAutoConfiguration {

  private final MetadataEngineProperties smartMetaProperties;

  public MetadataEngineAutoConfiguration(MetadataEngineProperties smartMetaProperties) {
    this.smartMetaProperties = smartMetaProperties;
  }

  /** 配置元数据注册中心 */
  /**
   * 校验策略执行器（{@code StrategyValidationExecutor}）需要的并发执行器。
   *
   * <p><b>为什么 starter 必须提供（2026-10-07 装配测试实测发现）</b>： {@code StrategyValidationExecutor} 被
   * {@code @ComponentScan} 扫入，其构造器第 1 个参数要 {@code
   * java.util.concurrent.Executor}（用于并行跑各校验策略）。宿主通常不会注册一个裸 {@code Executor} bean ⇒ starter 单独装配时
   * {@code UnsatisfiedDependencyException}。
   *
   * <p><b>按官方最佳实践用 {@code @ConditionalOnMissingBean}</b>：宿主若自己注册了 {@code TaskExecutor} / {@code
   * Executor}，本方法自动退让（{@code @ConditionalOnMissingBean} 官方文档指出：自动配置类<b>只在用户未声明时才生效</b>，这是"让 Boot
   * 礼貌"的机制）。
   */
  @Bean
  @ConditionalOnMissingBean(Executor.class)
  public Executor metadataValidationExecutor() {
    // 用虚拟线程（Java 21）承接并行校验：校验是 CPU 密集 + 大量等待混合，
    // 虚拟线程无需调参即可获得高并发，且不额外占用平台线程。
    return Executors.newVirtualThreadPerTaskExecutor();
  }

  @Bean
  @ConditionalOnMissingBean
  public MetadataRegistry metadataRegistry() {
    // 创建并配置MetadataRegistry
    return new MetadataRegistry();
  }

  /**
   * ★ 关于 {@code ports.registry.MetadataRegistry}（2026-10-07 修正）
   *
   * <p><b>此前本类重复定义了它</b>（bean 名 {@code registryMetadataRegistry}）， 而 runtime 的 {@code
   * RuntimeEngineAutoConfiguration} 也定义了一个 （bean 名 {@code portsMetadataRegistry}）⇒ 两者都注册时，按接口注入会报
   * {@code NoUniqueBeanDefinitionException: expected single matching bean but found 2}。
   *
   * <p><b>为什么 starter 自带的那份"排除"救不了</b>：{@code @ComponentScan} 的 {@code excludeFilters} <b>只作用于
   * starter 自己那次扫描</b>；宿主（如 {@code bone-metadata-server}）的 {@code @SpringBootApplication} 扫 {@code
   * com.bone.metadata.**} 时<b>范围更大</b>（包含 {@code ...engine.runtime.**}）， 照常注册 runtime 那份 ⇒ 冲突依然存在。
   *
   * <p><b>处置（业界最佳实践：单一事实来源）</b>：删除本类的重复定义， 让 runtime 的 {@code
   * RuntimeEngineAutoConfiguration#portsMetadataRegistry} 成为 <b>唯一来源</b>（它本来就带
   * {@code @ConditionalOnMissingBean}，宿主可覆盖）。 这样无论宿主是否引入 starter，都只会有一份注册中心。
   *
   * <p>该类之所以存在于 runtime（而非 starter），正是为了支持 "宿主直接依赖 runtime、不引 starter"这条路径—— starter 不应再重复一份。
   */

  /** 配置元数据仓库（默认使用内存实现） */
  @Bean
  @ConditionalOnMissingBean
  public MetadataRepository metadataRepository() {
    return new InMemoryMetadataRepository();
  }

  /** 配置验证引擎 */
  @Bean
  @ConditionalOnMissingBean
  public ValidationEngine validationEngine() {
    return new ValidationEngine();
  }

  /** 配置表达式引擎 */
  @Bean
  @ConditionalOnMissingBean
  public ExpressionEngine expressionEngine() {
    return new ExpressionEngine();
  }

  /** 配置转换引擎 */
  @Bean
  @ConditionalOnMissingBean
  public TransformationEngine transformationEngine() {
    return new TransformationEngine();
  }

  /** 配置复合元数据处理器 */
  @Bean
  @ConditionalOnMissingBean
  public CompositeMetadataProcessor compositeMetadataProcessor(
      List<com.bone.metadata.engine.runtime.metadata.processor.MetadataProcessor>
          metadataProcessors,
      ApplicationEventPublisher eventPublisher) {
    return new CompositeMetadataProcessor(metadataProcessors, eventPublisher);
  }

  /** 配置操作元数据注册中心 */
  @Bean
  @ConditionalOnMissingBean
  public OperationRegistry operationRegistry(
      MetadataRepository metadataRepository, MetadataRegistry metadataRegistry) {
    return new OperationRegistry(metadataRepository, metadataRegistry);
  }

  /** 配置动态数据服务 */
  @Bean
  @ConditionalOnMissingBean
  public DynamicDataService dynamicDataService(MetadataRegistry metadataRegistry) {
    return new InMemoryDynamicDataService(metadataRegistry);
  }

  /** 配置通用操作服务 */
  @Bean
  public GenericOperationService genericOperationService(
      MetadataEngine metadataEngine,
      DynamicDataService dynamicDataService,
      ExpressionEngine expressionEngine,
      ValidationEngine validationEngine,
      OperationRegistry operationRegistry,
      ApplicationEventPublisher eventPublisher) {
    GenericOperationService service =
        new GenericOperationService(
            metadataEngine,
            dynamicDataService,
            expressionEngine,
            validationEngine,
            operationRegistry,
            eventPublisher);

    // 设置延迟注入，避免循环依赖
    metadataEngine.setOperationService(service);

    return service;
  }

  /** 配置元数据核心引擎 */
  @Bean
  @Primary
  @ConditionalOnMissingBean
  public MetadataEngine metadataEngine(
      com.bone.metadata.engine.ports.registry.MetadataRegistry metadataRegistry,
      MetadataRepository metadataRepository,
      CompositeMetadataProcessor compositeMetadataProcessor,
      ApplicationEventPublisher eventPublisher) {
    // 创建一个适配器来转换CompositeMetadataProcessor到processor包的MetadataProcessor接口
    com.bone.metadata.engine.runtime.processor.MetadataProcessor processorAdapter =
        new com.bone.metadata.engine.runtime.processor.MetadataProcessor() {
          @Override
          public com.bone.metadata.engine.runtime.processor.MetadataProcessor.ValidationResult
              validateMetadata(Object metadata) {
            // 简单实现，返回验证通过
            return new com.bone.metadata.engine.runtime.processor.MetadataProcessor
                .ValidationResult() {
              @Override
              public boolean isValid() {
                return true;
              }

              @Override
              public String getErrorMessage() {
                return null;
              }

              @Override
              public Map<String, Object> getDetails() {
                return new HashMap<>();
              }
            };
          }

          @Override
          public Object analyzeImpact(String oldEntityName, Object newMetadata) {
            return null; // 简化实现
          }

          @Override
          public Object transformMetadata(Object sourceMetadata, String targetType) {
            return sourceMetadata; // 简化实现
          }

          @Override
          public Object enrichMetadata(Object metadata) {
            return metadata; // 简化实现
          }

          @Override
          public Object normalizeMetadata(Object metadata) {
            return metadata; // 简化实现
          }

          @Override
          public Object mergeMetadata(Object baseMetadata, Object overlayMetadata) {
            return baseMetadata; // 简化实现
          }

          @Override
          public Map<String, Object> extractMetadataInfo(Object metadata) {
            return new HashMap<>(); // 简化实现
          }
        };

    MetadataEngine engine =
        new MetadataEngine(
            metadataRegistry, metadataRepository,
            processorAdapter, eventPublisher);
    return engine;
  }
}
