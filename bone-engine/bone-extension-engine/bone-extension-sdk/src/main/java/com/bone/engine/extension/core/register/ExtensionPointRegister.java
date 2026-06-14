package com.bone.engine.extension.core.register;

import com.bone.engine.extension.api.annotation.EnableExtensionPoints;
import com.bone.engine.extension.api.annotation.ExtensionPoint;
import com.bone.engine.extension.core.proxy.ExtensionPointFactoryBean;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * 扩展点注册器 - 终极版（采用构造函数注入 Class 对象）
 *
 * <p>该类负责在 Spring Bean 定义阶段执行两个主要的注册任务: 1. 扫描被 @ExtensionPoint 注解的接口，并为每个接口注册一个代理 FactoryBean
 * (ExtensionPointFactoryBean)。 2. 扫描被 @Extension 注解的实现类，并注册它们的 Bean 定义。
 *
 * @author renhui.trh 2023-10-30
 */
public final class ExtensionPointRegister
    implements ImportBeanDefinitionRegistrar, ResourceLoaderAware, EnvironmentAware {
  private ResourceLoader resourceLoader;
  private Environment environment;

  ExtensionPointRegister() {
    // 默认构造函数
  }

  // --- 接口实现 ---

  @Override
  public void setResourceLoader(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void registerBeanDefinitions(
      AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
    // 步骤 1: 注册扩展点代理定义 (FactoryBean)
    this.registerExtensionPoints(metadata, registry);

    // 步骤 2: 注册扩展实现类定义 (实际实现者)
    this.registerExtensions(metadata, registry);
  }

  // --- 核心注册逻辑 ---

  /**
   * 注册被 @Extension 注解的类（扩展实现）的 Bean 定义。 这些定义将被 Spring 实例化，其实例将在稍后由一个独立的组件（如实现
   * SmartInitializingSingleton 的 ExtensionRegister）收集和激活。
   */
  private void registerExtensions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
    // 假设 ExtensionBeanDefinitionScanner 处理 @Extension 类的实际扫描和注册。
    // 注册器（registry）被传递，以便扫描器可以直接注册 Bean 定义。
    ExtensionBeanDefinitionScanner scanner = new ExtensionBeanDefinitionScanner(registry);
    scanner.scan(getBasePackages(metadata));
  }

  /** 扫描 @ExtensionPoint 接口，并为每个接口注册一个代理 FactoryBean。 */
  public void registerExtensionPoints(
      AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
    LinkedHashSet<BeanDefinition> candidateComponents = new LinkedHashSet();
    ClassPathScanningCandidateComponentProvider scanner = this.getScanner();
    scanner.setResourceLoader(this.resourceLoader);
    // 针对 @ExtensionPoint 标记接口的包含过滤器
    scanner.addIncludeFilter(new AnnotationTypeFilter(ExtensionPoint.class));

    String[] basePackages = this.getBasePackages(metadata);
    for (String basePackage : basePackages) {
      candidateComponents.addAll(scanner.findCandidateComponents(basePackage));
    }

    Map<String, Object> attrs =
        metadata.getAnnotationAttributes(EnableExtensionPoints.class.getCanonicalName());
    registerFactoryBeanDefinition(metadata, registry, attrs, candidateComponents);
  }

  /** 将发现的 @ExtensionPoint 接口定义转换为 FactoryBean 定义。 */
  private void registerFactoryBeanDefinition(
      AnnotationMetadata metadata,
      BeanDefinitionRegistry registry,
      Map<String, Object> attrs,
      LinkedHashSet<BeanDefinition> candidateComponents) {

    for (BeanDefinition candidateComponent : candidateComponents) {

      if (candidateComponent instanceof AnnotatedBeanDefinition annotatedBeanDefinition) {
        AnnotationMetadata annotationMetadata = annotatedBeanDefinition.getMetadata();
        // 确保只有接口被 @ExtensionPoint 注解
        Assert.isTrue(
            annotationMetadata.isInterface(),
            "@ExtensionPoint can only be specified on an interface");
        String className = annotationMetadata.getClassName(); // 扩展点接口的 FQCN

        // 1. 创建 FactoryBean 的 BeanDefinition
        GenericBeanDefinition factoryBeanDefinition = new GenericBeanDefinition();
        factoryBeanDefinition.setBeanClass(ExtensionPointFactoryBean.class);
        factoryBeanDefinition.setPrimary(true); // 关键：确保该代理 Bean 在注入时被优先选择
        factoryBeanDefinition.setAutowireCandidate(true);
        factoryBeanDefinition.setScope(BeanDefinition.SCOPE_SINGLETON);

        // 2. 最佳实践：使用构造函数注入 Class 对象
        try {
          Class<?> extPointInterface = Class.forName(className);

          // 将 Class<?> 对象作为第一个构造函数参数（索引 0）
          factoryBeanDefinition
              .getConstructorArgumentValues()
              .addIndexedArgumentValue(0, extPointInterface);
        } catch (ClassNotFoundException e) {
          throw new IllegalStateException("Failed to load ExtensionPoint class: " + className, e);
        }

        // 3. 使用接口的 FQCN 作为 Bean 名称进行注册
        BeanDefinitionHolder holder = new BeanDefinitionHolder(factoryBeanDefinition, className);
        BeanDefinitionReaderUtils.registerBeanDefinition(holder, registry);
      }
    }
  }

  // --- 辅助方法 ---

  /** 创建一个配置为查找独立组件的扫描器。 */
  protected ClassPathScanningCandidateComponentProvider getScanner() {
    return new ClassPathScanningCandidateComponentProvider(false, this.environment) {
      @Override
      protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
        // 确保只有独立（非内部类）且非注解的类/接口是候选者。
        return beanDefinition.getMetadata().isIndependent()
            && !beanDefinition.getMetadata().isAnnotation();
      }
    };
  }

  /** 从 @EnableExtensionPoints 注解中确定要扫描的基础包路径。 */
  protected String[] getBasePackages(AnnotationMetadata importingClassMetadata) {
    Map<String, Object> attributes =
        importingClassMetadata.getAnnotationAttributes(
            EnableExtensionPoints.class.getCanonicalName());
    Set<String> basePackages = new HashSet();

    // 获取显式定义的基础包路径
    String[] basePackagesArr = (String[]) attributes.get("basePackages");
    for (String item : basePackagesArr) {
      if (StringUtils.hasText(item)) basePackages.add(item);
    }

    // 如果没有显式定义包路径，则使用 @EnableExtensionPoints 所在类的包路径
    if (basePackages.isEmpty()) {
      basePackages.add(ClassUtils.getPackageName(importingClassMetadata.getClassName()));
    }

    return basePackages.toArray(new String[basePackages.size()]);
  }
}
