package com.bone.metadata.sdk.support.util;

import com.bone.metadata.sdk.Repository;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;

/** 类工具类，提供通用的类操作功能 */
public class RepositoryClassUtils {
  private static final Logger logger = LoggerFactory.getLogger(RepositoryClassUtils.class);
  private static final Map<String, Class<?>[]> GENERIC_CACHE = new ConcurrentHashMap<>();

  /**
   * 判断类型是否为简单类型
   *
   * @param type 要检查的类型
   * @return 如果是简单类型返回true，否则返回false
   */
  public static boolean isSimpleType(Class<?> type) {
    return type.isPrimitive()
        || Number.class.isAssignableFrom(type)
        || CharSequence.class.isAssignableFrom(type)
        || Boolean.class.equals(type)
        || java.util.Date.class.isAssignableFrom(type)
        || java.time.temporal.Temporal.class.isAssignableFrom(type)
        || type == Object.class;
  }

  /**
   * 解析Repository接口的泛型参数类型
   *
   * @param repoInterface Repository接口类
   * @return 包含实体类和ID类的数组，如果解析失败返回null
   */
  public static Class<?>[] resolveGenericTypes(Class<?> repoInterface) {
    // 使用缓存避免重复解析
    return GENERIC_CACHE.computeIfAbsent(
        repoInterface.getName(),
        key -> {
          // 检查直接实现的泛型接口
          for (Type genericInterface : repoInterface.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType pt) {
              if (pt.getRawType() instanceof Class<?> rawType
                  && Repository.class.isAssignableFrom(rawType)) {
                Type[] actualTypes = pt.getActualTypeArguments();
                if (actualTypes.length >= 2) {
                  try {
                    // 处理实际类型参数
                    if (actualTypes[0] instanceof Class<?> entityClass
                        && actualTypes[1] instanceof Class<?> idClass) {
                      return new Class<?>[] {entityClass, idClass};
                    } else {
                      // 尝试通过类名加载
                      Class<?> entityClass = Class.forName(actualTypes[0].getTypeName());
                      Class<?> idClass = Class.forName(actualTypes[1].getTypeName());
                      return new Class<?>[] {entityClass, idClass};
                    }
                  } catch (ClassNotFoundException e) {
                    logger.error(
                        "Failed to resolve generic types for {}", repoInterface.getName(), e);
                  }
                }
              }
            }
          }

          // 如果没有直接实现，递归检查父接口
          for (Class<?> parentInterface : repoInterface.getInterfaces()) {
            Class<?>[] parentTypes = resolveGenericTypes(parentInterface);
            if (parentTypes != null) {
              return parentTypes;
            }
          }

          return new Class<?>[0];
        });
  }

  /**
   * 检查接口是否直接或间接实现了Repository接口
   *
   * @param repoInterface 要检查的接口类
   * @return 如果实现了Repository接口返回true，否则返回false
   */
  public static boolean checkIndirectRepositoryImplementation(Class<?> repoInterface) {
    // 检查所有父接口
    for (Class<?> parentInterface : repoInterface.getInterfaces()) {
      if (parentInterface.getName().equals(Repository.class.getName())) {
        return true;
      }

      // 递归检查父接口的父接口
      if (checkIndirectRepositoryImplementation(parentInterface)) {
        return true;
      }
    }
    return false;
  }

  /** 检查对象是否有指定名称的方法 */
  public static boolean hasMethod(Object obj, String methodName) {
    try {
      obj.getClass().getMethod(methodName);
      return true;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  /**
   * 从方法或类中查找指定类型的注解
   *
   * @param <A> 要检索的注解类型
   * @param element 要检查注解的方法或类
   * @param annotationType 要检索的注解类
   * @return 注解实例（如找到），否则返回null
   */
  public static <A extends Annotation> A findAnnotation(Object element, Class<A> annotationType) {
    if (element instanceof Method) {
      return AnnotationUtils.findAnnotation((Method) element, annotationType);
    } else if (element instanceof Class) {
      return AnnotationUtils.findAnnotation((Class<?>) element, annotationType);
    }
    return null;
  }

  /**
   * 执行带有重试机制的操作，处理并发冲突情况
   *
   * @param action 要执行的操作
   * @param maxAttempts 最大尝试次数
   * @param exceptionType 要捕获并重试的异常类型
   * @param errorMessage 失败时的错误消息
   * @param <E> 异常类型
   * @throws E 当达到最大重试次数时抛出指定类型的异常
   */
  public static <E extends Exception> void runWithRetry(
      Runnable action, int maxAttempts, Class<E> exceptionType, String errorMessage) throws E {
    int attempts = 0;
    Random rnd = new Random();
    while (true) {
      try {
        action.run();
        return;
      } catch (Exception ex) {
        if (!exceptionType.isInstance(ex)) {
          throw ex;
        }

        if (++attempts > maxAttempts) {
          E exception;
          try {
            exception =
                exceptionType
                    .getDeclaredConstructor(String.class, Throwable.class)
                    .newInstance(errorMessage + "，次数：" + maxAttempts, ex);
          } catch (ReflectiveOperationException e) {
            @SuppressWarnings("unchecked")
            E result = (E) new RuntimeException(errorMessage, ex);
            throw result;
          }
          throw exception;
        }

        // 指数退避 + 随机抖动
        long backoff = (50L << Math.min(attempts, 10)) + rnd.nextInt(50);
        try {
          TimeUnit.MILLISECONDS.sleep(backoff);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          E exception;
          try {
            exception =
                exceptionType
                    .getDeclaredConstructor(String.class, Throwable.class)
                    .newInstance("操作重试被中断", ie);
          } catch (ReflectiveOperationException e) {
            @SuppressWarnings("unchecked")
            E result = (E) new RuntimeException("操作重试被中断", ie);
            throw result;
          }
          throw exception;
        }
      }
    }
  }
}
