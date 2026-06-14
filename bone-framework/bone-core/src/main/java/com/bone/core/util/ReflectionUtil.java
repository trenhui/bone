package com.bone.core.util;

import java.io.Serial;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.ref.SoftReference;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * 高性能反射工具类 - 生产级最佳实践 支持缓存、类型安全、方法句柄优化
 *
 * @author Bone Framework Team
 * @since 2025
 */
@Slf4j
public final class ReflectionUtil {

  // 缓存配置
  private static final int INITIAL_CACHE_SIZE = 256;
  private static final float LOAD_FACTOR = 0.75f;
  private static final int CONCURRENCY_LEVEL = 16;

  // 方法缓存（软引用避免内存泄漏）
  private static final Map<MethodSignature, SoftReference<Method>> METHOD_CACHE =
      new ConcurrentHashMap<>(INITIAL_CACHE_SIZE, LOAD_FACTOR, CONCURRENCY_LEVEL);

  // 字段缓存
  private static final Map<FieldSignature, SoftReference<Field>> FIELD_CACHE =
      new ConcurrentHashMap<>(INITIAL_CACHE_SIZE, LOAD_FACTOR, CONCURRENCY_LEVEL);

  // 方法句柄缓存（JDK 17+ 性能优化）
  private static final Map<MethodSignature, SoftReference<MethodHandle>> METHOD_HANDLE_CACHE =
      new ConcurrentHashMap<>(INITIAL_CACHE_SIZE / 2, LOAD_FACTOR, CONCURRENCY_LEVEL);

  // 构造器缓存
  private static final Map<ConstructorSignature, SoftReference<Constructor<?>>> CONSTRUCTOR_CACHE =
      new ConcurrentHashMap<>(INITIAL_CACHE_SIZE / 4, LOAD_FACTOR, CONCURRENCY_LEVEL);

  // 基本类型装箱兼容性映射（预计算提升性能）
  private static final Map<Class<?>, Class<?>> BOXING_COMPATIBILITY_MAP =
      createBoxingCompatibilityMap();

  private ReflectionUtil() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  // === 公共API - 方法调用 ===

  /**
   * 获取带有指定注解的接口类型
   *
   * @param targetClass 目标类
   * @param annotationClass 注解类
   * @param <A> 注解类型
   * @return 带有指定注解的接口类型
   */
  @Nullable
  public static <A extends Annotation> Class<A> getInterfaceByAnnotation(
      @Nullable Class<?> targetClass, @Nullable Class<A> annotationClass) {
    // 检查目标类是否为空
    if (targetClass == null || annotationClass == null) {
      return null;
    }

    Class<?> currentClass = targetClass;

    // 遍历类层次结构，查找带有指定注解的接口
    while (currentClass != null) {
      for (Type type : currentClass.getInterfaces()) {
        if (type instanceof Class) {
          Class<?> interfaceClass = (Class<?>) type;
          if (interfaceClass.isInterface()
              && interfaceClass.getAnnotation(annotationClass) != null) {
            @SuppressWarnings("unchecked")
            Class<A> result = (Class<A>) interfaceClass;
            return result;
          }
        }
      }
      // 向上查找父类
      currentClass = currentClass.getSuperclass();
    }
    // 如果没有找到，返回null
    return null;
  }

  /**
   * 使用反射创建一个新的实例
   *
   * @param clazz 要实例化的类
   * @param parameterTypes 构造函数的参数类型
   * @param args 构造函数的参数
   * @return 新创建的实例
   */
  @NonNull
  public static Object newInstance(
      @NonNull Class<?> clazz, @Nullable Class<?>[] parameterTypes, @Nullable Object[] args) {
    Assert.notNull(clazz, "Class cannot be null");

    try {
      if (parameterTypes == null || parameterTypes.length == 0) {
        // 无参构造
        Constructor<?> constructor = findConstructor(clazz, new Class<?>[0]);
        ensureAccessible(constructor, null);
        return constructor.newInstance();
      } else {
        // 有参构造
        Constructor<?> constructor = findConstructor(clazz, parameterTypes);
        ensureAccessible(constructor, null);
        return constructor.newInstance(args);
      }
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new ReflectionException("Failed to create instance of " + clazz.getName(), e);
    }
  }

  /** 调用对象方法 */
  @SuppressWarnings("unchecked")
  @Nullable
  public static <T> T invokeMethod(
      @NonNull Object target, @NonNull String methodName, @Nullable Object... arguments) {
    Assert.notNull(target, "Target object cannot be null");
    Assert.hasText(methodName, "Method name cannot be blank");

    try {
      // 优先使用高性能方法句柄
      MethodHandle methodHandle = findMethodHandle(target.getClass(), methodName, arguments);
      if (methodHandle != null) {
        return invokeWithMethodHandle(methodHandle, target, arguments);
      }

      // 回退到传统反射调用
      return invokeWithReflection(target, methodName, arguments);

    } catch (ReflectionException e) {
      throw e; // 重新抛出已知异常
    } catch (Exception e) {
      throw new ReflectionException("Method invocation failed: " + methodName, e);
    }
  }

  /** 静态方法调用 */
  @SuppressWarnings("unchecked")
  @Nullable
  public static <T> T invokeStaticMethod(
      @NonNull Class<?> clazz, @NonNull String methodName, @Nullable Object... arguments) {
    Assert.notNull(clazz, "Class cannot be null");
    Assert.hasText(methodName, "Method name cannot be blank");

    try {
      Method method = findMethod(clazz, methodName, arguments);
      ensureAccessible(method, null); // 静态方法传入 null
      return (T) method.invoke(null, arguments);
    } catch (IllegalAccessException e) {
      throw new ReflectionException("Static method access denied: " + methodName, e);
    } catch (InvocationTargetException e) {
      throw new ReflectionException(
          "Static method execution failed: " + methodName, e.getTargetException());
    }
  }

  // === 公共API - 字段操作 ===

  /** 设置字段值 */
  public static void setFieldValue(
      @NonNull Object target, @NonNull String fieldName, @Nullable Object value) {
    Assert.notNull(target, "Target object cannot be null");
    Assert.hasText(fieldName, "Field name cannot be blank");

    try {
      Field field = findField(target.getClass(), fieldName);
      ensureAccessible(field, target);
      field.set(target, value);
    } catch (IllegalAccessException e) {
      throw new ReflectionException("Field assignment failed: " + fieldName, e);
    }
  }

  /** 获取字段值 */
  @SuppressWarnings("unchecked")
  @Nullable
  public static <T> T getFieldValue(@NonNull Object target, @NonNull String fieldName) {
    Assert.notNull(target, "Target object cannot be null");
    Assert.hasText(fieldName, "Field name cannot be blank");

    try {
      Field field = findField(target.getClass(), fieldName);
      ensureAccessible(field, target);
      return (T) field.get(target);
    } catch (IllegalAccessException e) {
      throw new ReflectionException("Field retrieval failed: " + fieldName, e);
    }
  }

  /** 设置静态字段值 */
  public static void setStaticFieldValue(
      @NonNull Class<?> clazz, @NonNull String fieldName, @Nullable Object value) {
    Assert.notNull(clazz, "Class cannot be null");
    Assert.hasText(fieldName, "Field name cannot be blank");

    try {
      Field field = findField(clazz, fieldName);
      ensureAccessible(field, null); // 静态字段传入 null
      field.set(null, value);
    } catch (IllegalAccessException e) {
      throw new ReflectionException("Static field assignment failed: " + fieldName, e);
    }
  }

  /** 获取静态字段值 */
  @SuppressWarnings("unchecked")
  @Nullable
  public static <T> T getStaticFieldValue(@NonNull Class<?> clazz, @NonNull String fieldName) {
    Assert.notNull(clazz, "Class cannot be null");
    Assert.hasText(fieldName, "Field name cannot be blank");

    try {
      Field field = findField(clazz, fieldName);
      ensureAccessible(field, null); // 静态字段传入 null
      return (T) field.get(null);
    } catch (IllegalAccessException e) {
      throw new ReflectionException("Static field retrieval failed: " + fieldName, e);
    }
  }

  // === 核心查找逻辑 ===

  /** 查找方法（带缓存和兼容性匹配） */
  @NonNull
  private static Method findMethod(
      @NonNull Class<?> clazz, @NonNull String methodName, @Nullable Object[] arguments) {
    MethodSignature signature =
        new MethodSignature(clazz, methodName, getParameterTypes(arguments));

    return METHOD_CACHE
        .compute(
            signature,
            (key, existingRef) -> {
              try {
                Method method = (existingRef != null) ? existingRef.get() : null;
                if (method == null) {
                  method = locateMethod(clazz, methodName, getParameterTypes(arguments));
                }
                return new SoftReference<>(method);
              } catch (NoSuchMethodException e) {
                throw new ReflectionException("Method not found: " + methodName, e);
              }
            })
        .get();
  }

  /** 查找方法句柄（高性能路径） */
  @Nullable
  private static MethodHandle findMethodHandle(
      @NonNull Class<?> clazz, @NonNull String methodName, @Nullable Object[] arguments) {
    try {
      MethodSignature signature =
          new MethodSignature(clazz, methodName, getParameterTypes(arguments));

      SoftReference<MethodHandle> handleRef =
          METHOD_HANDLE_CACHE.compute(
              signature,
              (key, existingRef) -> {
                try {
                  MethodHandle handle = (existingRef != null) ? existingRef.get() : null;
                  if (handle == null) {
                    Method method = locateMethod(clazz, methodName, getParameterTypes(arguments));
                    ensureAccessible(method, null); // 方法句柄不需要具体实例
                    handle = MethodHandles.lookup().unreflect(method);
                  }
                  return new SoftReference<>(handle);
                } catch (IllegalAccessException | NoSuchMethodException e) {
                  log.debug(
                      "Method handle creation failed for {}.{}",
                      clazz.getSimpleName(),
                      methodName,
                      e);
                  return null;
                }
              });

      return handleRef != null ? handleRef.get() : null;
    } catch (Exception e) {
      log.debug("Method handle lookup failed for {}.{}", clazz.getSimpleName(), methodName, e);
      return null;
    }
  }

  /** 定位方法实现 */
  @NonNull
  private static Method locateMethod(
      @NonNull Class<?> clazz, @NonNull String methodName, @Nullable Class<?>[] parameterTypes)
      throws NoSuchMethodException {
    try {
      return clazz.getMethod(methodName, parameterTypes != null ? parameterTypes : new Class<?>[0]);
    } catch (NoSuchMethodException e) {
      return findCompatibleMethod(clazz, methodName, parameterTypes)
          .orElseThrow(
              () ->
                  new NoSuchMethodException(
                      String.format("Method %s not found in %s", methodName, clazz.getName())));
    }
  }

  /** 查找兼容方法 */
  @NonNull
  private static Optional<Method> findCompatibleMethod(
      @NonNull Class<?> clazz, @NonNull String methodName, @Nullable Class<?>[] parameterTypes) {
    Class<?>[] actualParameterTypes = parameterTypes != null ? parameterTypes : new Class<?>[0];

    return Arrays.stream(clazz.getMethods())
        .filter(method -> method.getName().equals(methodName))
        .filter(method -> areParametersCompatible(method.getParameterTypes(), actualParameterTypes))
        .max(
            Comparator.comparingInt(
                method ->
                    calculateCompatibilityScore(method.getParameterTypes(), actualParameterTypes)));
  }

  /** 查找字段（带缓存） */
  @NonNull
  private static Field findField(@NonNull Class<?> clazz, @NonNull String fieldName) {
    FieldSignature signature = new FieldSignature(clazz, fieldName);

    return FIELD_CACHE
        .compute(
            signature,
            (key, existingRef) -> {
              try {
                Field field = (existingRef != null) ? existingRef.get() : null;
                if (field == null) {
                  field = locateField(clazz, fieldName);
                }
                return new SoftReference<>(field);
              } catch (NoSuchFieldException e) {
                throw new ReflectionException("Field not found: " + fieldName, e);
              }
            })
        .get();
  }

  /** 定位字段实现（支持继承链） */
  @NonNull
  private static Field locateField(@NonNull Class<?> clazz, @NonNull String fieldName)
      throws NoSuchFieldException {
    for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
      try {
        return current.getDeclaredField(fieldName);
      } catch (NoSuchFieldException ignored) {
        // 继续在父类中查找
      }
    }
    throw new NoSuchFieldException("Field '" + fieldName + "' not found in " + clazz.getName());
  }

  /** 查找构造器（带缓存） */
  @NonNull
  private static Constructor<?> findConstructor(
      @NonNull Class<?> clazz, @Nullable Class<?>[] parameterTypes) {
    ConstructorSignature signature = new ConstructorSignature(clazz, parameterTypes);

    return CONSTRUCTOR_CACHE
        .compute(
            signature,
            (key, existingRef) -> {
              try {
                Constructor<?> constructor = (existingRef != null) ? existingRef.get() : null;
                if (constructor == null) {
                  constructor = locateConstructor(clazz, parameterTypes);
                }
                return new SoftReference<>(constructor);
              } catch (NoSuchMethodException e) {
                throw new ReflectionException("Constructor not found for " + clazz.getName(), e);
              }
            })
        .get();
  }

  /** 定位构造器实现 */
  @NonNull
  private static Constructor<?> locateConstructor(
      @NonNull Class<?> clazz, @Nullable Class<?>[] parameterTypes) throws NoSuchMethodException {
    Class<?>[] actualParameterTypes = parameterTypes != null ? parameterTypes : new Class<?>[0];
    return clazz.getConstructor(actualParameterTypes);
  }

  // === 兼容性检查 ===

  /** 参数兼容性检查 */
  private static boolean areParametersCompatible(Class<?>[] methodParams, Class<?>[] argParams) {
    if (methodParams.length != argParams.length) {
      return false;
    }

    return IntStream.range(0, methodParams.length)
        .allMatch(i -> isTypeAssignable(methodParams[i], argParams[i]));
  }

  /** 类型可赋值性检查 */
  private static boolean isTypeAssignable(Class<?> paramType, Class<?> argType) {
    if (argType == null) {
      return !paramType.isPrimitive(); // null 不能赋值给基本类型
    }
    return paramType.isAssignableFrom(argType) || isBoxingCompatible(paramType, argType);
  }

  /** 基本类型装箱兼容性检查 */
  private static boolean isBoxingCompatible(Class<?> paramType, Class<?> argType) {
    return BOXING_COMPATIBILITY_MAP.get(paramType) == argType;
  }

  /** 计算兼容性分数 */
  private static int calculateCompatibilityScore(Class<?>[] methodParams, Class<?>[] argParams) {
    return IntStream.range(0, methodParams.length)
        .map(
            i -> {
              if (methodParams[i].equals(argParams[i])) return 10;
              if (methodParams[i].isAssignableFrom(argParams[i])) return 5;
              if (isBoxingCompatible(methodParams[i], argParams[i])) return 3;
              return 0;
            })
        .sum();
  }

  // === 工具方法 ===

  /** 使用方法句柄调用方法 */
  @SuppressWarnings("unchecked")
  @Nullable
  private static <T> T invokeWithMethodHandle(
      @NonNull MethodHandle methodHandle, @NonNull Object target, @Nullable Object[] arguments) {
    try {
      if (arguments == null || arguments.length == 0) {
        return (T) methodHandle.invoke(target);
      }

      // 构建参数数组
      Object[] argsWithTarget = new Object[arguments.length + 1];
      argsWithTarget[0] = target;
      System.arraycopy(arguments, 0, argsWithTarget, 1, arguments.length);

      return (T) methodHandle.invokeWithArguments(argsWithTarget);
    } catch (Throwable e) {
      // 将 Throwable 转换为 RuntimeException，保持原始异常
      if (e instanceof RuntimeException) {
        throw (RuntimeException) e;
      } else if (e instanceof Error) {
        throw (Error) e;
      } else {
        throw new ReflectionException("Method handle invocation failed", e);
      }
    }
  }

  /** 使用反射调用方法 */
  @SuppressWarnings("unchecked")
  @Nullable
  private static <T> T invokeWithReflection(
      @NonNull Object target, @NonNull String methodName, @Nullable Object[] arguments) {
    try {
      Method method = findMethod(target.getClass(), methodName, arguments);
      ensureAccessible(method, target);
      return (T) method.invoke(target, arguments);
    } catch (InvocationTargetException e) {
      // 直接抛出原始异常，保持调用栈完整性
      Throwable targetException = e.getTargetException();
      if (targetException instanceof RuntimeException) {
        throw (RuntimeException) targetException;
      } else if (targetException instanceof Error) {
        throw (Error) targetException;
      } else {
        throw new ReflectionException("Method execution failed: " + methodName, targetException);
      }
    } catch (IllegalAccessException e) {
      throw new ReflectionException("Method access denied: " + methodName, e);
    }
  }

  /**
   * 确保可访问性（修复版本）
   *
   * @param accessible 可访问对象（Field/Method/Constructor）
   * @param target 目标对象，对于静态成员传入 null
   */
  private static void ensureAccessible(
      @NonNull AccessibleObject accessible, @Nullable Object target) {
    if (!accessible.canAccess(target)) {
      // JDK17+ 优化：直接设置可访问性
      accessible.setAccessible(true);
    }
  }

  /** 获取参数类型数组 */
  @NonNull
  private static Class<?>[] getParameterTypes(@Nullable Object[] arguments) {
    if (arguments == null) {
      return new Class[0];
    }
    return Arrays.stream(arguments)
        .map(arg -> arg != null ? arg.getClass() : Object.class)
        .toArray(Class<?>[]::new);
  }

  /** 创建装箱兼容性映射 */
  private static Map<Class<?>, Class<?>> createBoxingCompatibilityMap() {
    Map<Class<?>, Class<?>> map = new HashMap<>(32);

    // 基本类型 -> 包装类型
    map.put(int.class, Integer.class);
    map.put(long.class, Long.class);
    map.put(double.class, Double.class);
    map.put(float.class, Float.class);
    map.put(boolean.class, Boolean.class);
    map.put(byte.class, Byte.class);
    map.put(char.class, Character.class);
    map.put(short.class, Short.class);
    map.put(void.class, Void.class);

    // 包装类型 -> 基本类型
    map.put(Integer.class, int.class);
    map.put(Long.class, long.class);
    map.put(Double.class, double.class);
    map.put(Float.class, float.class);
    map.put(Boolean.class, boolean.class);
    map.put(Byte.class, byte.class);
    map.put(Character.class, char.class);
    map.put(Short.class, short.class);
    map.put(Void.class, void.class);

    return Collections.unmodifiableMap(map);
  }

  // === 缓存管理 ===

  /** 清空方法缓存 */
  public static void clearMethodCache() {
    METHOD_CACHE.clear();
    METHOD_HANDLE_CACHE.clear();
    log.debug("Method cache cleared");
  }

  /** 清空字段缓存 */
  public static void clearFieldCache() {
    FIELD_CACHE.clear();
    log.debug("Field cache cleared");
  }

  /** 清空构造器缓存 */
  public static void clearConstructorCache() {
    CONSTRUCTOR_CACHE.clear();
    log.debug("Constructor cache cleared");
  }

  /** 清空所有缓存 */
  public static void clearAllCaches() {
    clearMethodCache();
    clearFieldCache();
    clearConstructorCache();
    log.debug("All reflection caches cleared");
  }

  /** 获取缓存统计信息 */
  @NonNull
  public static CacheStats getCacheStats() {
    return new CacheStats(
        METHOD_CACHE.size(),
        FIELD_CACHE.size(),
        METHOD_HANDLE_CACHE.size(),
        CONSTRUCTOR_CACHE.size());
  }

  // === 内部记录类 ===

  /** 方法签名 */
  private record MethodSignature(Class<?> clazz, String methodName, Class<?>[] parameterTypes) {
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof MethodSignature that)) return false;
      return Objects.equals(clazz, that.clazz)
          && Objects.equals(methodName, that.methodName)
          && Arrays.equals(parameterTypes, that.parameterTypes);
    }

    @Override
    public int hashCode() {
      return Objects.hash(clazz, methodName, Arrays.hashCode(parameterTypes));
    }

    @Override
    public String toString() {
      return clazz.getSimpleName()
          + "."
          + methodName
          + Arrays.stream(parameterTypes)
              .map(Class::getSimpleName)
              .collect(Collectors.joining(",", "(", ")"));
    }
  }

  /** 字段签名 */
  private record FieldSignature(Class<?> clazz, String fieldName) {
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof FieldSignature that)) return false;
      return Objects.equals(clazz, that.clazz) && Objects.equals(fieldName, that.fieldName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(clazz, fieldName);
    }

    @Override
    public String toString() {
      return clazz.getSimpleName() + "." + fieldName;
    }
  }

  /** 构造器签名 */
  private record ConstructorSignature(Class<?> clazz, Class<?>[] parameterTypes) {
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ConstructorSignature that)) return false;
      return Objects.equals(clazz, that.clazz)
          && Arrays.equals(parameterTypes, that.parameterTypes);
    }

    @Override
    public int hashCode() {
      return Objects.hash(clazz, Arrays.hashCode(parameterTypes));
    }

    @Override
    public String toString() {
      return clazz.getSimpleName()
          + "("
          + Arrays.stream(parameterTypes != null ? parameterTypes : new Class<?>[0])
              .map(Class::getSimpleName)
              .collect(Collectors.joining(", "))
          + ")";
    }
  }

  /** 缓存统计信息 */
  public record CacheStats(
      int methodCacheSize,
      int fieldCacheSize,
      int methodHandleCacheSize,
      int constructorCacheSize) {
    @Override
    public String toString() {
      return String.format(
          "MethodCache: %d, FieldCache: %d, MethodHandleCache: %d, ConstructorCache: %d",
          methodCacheSize, fieldCacheSize, methodHandleCacheSize, constructorCacheSize);
    }
  }

  /** 反射异常 */
  public static class ReflectionException extends RuntimeException {
    @Serial private static final long serialVersionUID = 1L;

    public ReflectionException(String message) {
      super(message);
    }

    public ReflectionException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
