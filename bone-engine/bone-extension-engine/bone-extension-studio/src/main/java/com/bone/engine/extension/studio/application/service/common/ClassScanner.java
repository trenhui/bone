package com.bone.engine.extension.studio.application.service.common;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.util.ClassUtils;

/** 类扫描工具类 提供通用的类扫描、加载和处理功能 */
public class ClassScanner {

  private static final Logger log = LoggerFactory.getLogger(ClassScanner.class);

  /**
   * 扫描指定包下的类并处理符合条件的类
   *
   * @param basePackages 基础包名列表
   * @param classFilter 类过滤条件
   * @param classProcessor 类处理器
   * @return 成功处理的类数量
   */
  public static int scanAndProcessClasses(
      List<String> basePackages,
      Predicate<Class<?>> classFilter,
      Consumer<Class<?>> classProcessor) {
    int processedCount = 0;

    if (basePackages == null || basePackages.isEmpty()) {
      log.warn("扫描包列表为空，跳过扫描");
      return 0;
    }

    for (String basePackage : basePackages) {
      basePackage = basePackage.trim();
      if (basePackage.isEmpty()) {
        log.warn("跳过空包名");
        continue;
      }

      log.debug("开始扫描包: {}", basePackage);
      String searchPath = "classpath*:" + basePackage.replace(".", "/") + "/**/*.class";

      try {
        Set<Resource> resources = ResourceUtils.getResources(searchPath);
        log.debug("包 {} 下扫描到 {} 个资源", basePackage, resources.size());

        for (Resource resource : resources) {
          try {
            if (!resource.exists() || !resource.isReadable()) {
              log.warn("资源不可用或不可读: {}", resource.getURI());
              continue;
            }

            // 解析资源为类文件
            String className = ResourceUtils.getClassNameFromResource(resource, basePackage);
            if (className == null) {
              log.debug("无法从资源中提取类名: {}", resource.getURI());
              continue;
            }

            // 加载类
            Class<?> clazz = null;
            try {
              clazz = ClassUtils.forName(className, ClassUtils.getDefaultClassLoader());
            } catch (ClassNotFoundException e) {
              log.warn("类未找到: {}", className);
              continue;
            }

            // 过滤并处理类
            if (classFilter.test(clazz)) {
              classProcessor.accept(clazz);
              processedCount++;
            }
          } catch (Exception e) {
            log.warn("处理资源时出错: {}", resource.getURI(), e);
          }
        }
      } catch (Exception e) {
        log.warn("扫描包时出错: {}", basePackage, e);
      }
    }

    return processedCount;
  }

  /**
   * 扫描指定包下的类并处理带有特定注解的类
   *
   * @param basePackages 基础包名列表
   * @param annotationClass 注解类
   * @param classProcessor 类处理器
   * @param <A> 注解类型
   * @return 成功处理的类数量
   */
  public static <A extends java.lang.annotation.Annotation> int scanAndProcessAnnotatedClasses(
      List<String> basePackages,
      Class<A> annotationClass,
      ClassProcessorWithAnnotation<A> classProcessor) {

    return scanAndProcessClasses(
        basePackages,
        clazz -> clazz.isAnnotationPresent(annotationClass),
        clazz -> {
          A annotation = clazz.getAnnotation(annotationClass);
          if (annotation != null) {
            classProcessor.process(clazz, annotation);
          }
        });
  }

  /**
   * 处理带有注解的类的函数式接口
   *
   * @param <A> 注解类型
   */
  @FunctionalInterface
  public interface ClassProcessorWithAnnotation<A extends java.lang.annotation.Annotation> {
    void process(Class<?> clazz, A annotation);
  }

  /**
   * 解析配置的基础包列表
   *
   * @param scanBasePackages 配置的基础包字符串，多个包以逗号分隔
   * @return 基础包列表
   */
  public static List<String> parseBasePackages(String scanBasePackages) {
    if (scanBasePackages == null || scanBasePackages.trim().isEmpty()) {
      return List.of();
    }
    return Arrays.stream(scanBasePackages.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());
  }
}
