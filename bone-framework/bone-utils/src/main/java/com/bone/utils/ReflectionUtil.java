package com.bone.utils;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * ReflectionUtil
 * 提供了一些反射操作的工具方法
 *
 * @author renhui.trh 2023-10-30
 */
public class ReflectionUtil {
    // 缓存Method对象以提高性能
    private static final ConcurrentMap<String, Method> methodCache = new ConcurrentHashMap<>();
    // 缓存MethodHandle对象以提高性能
    private static final ConcurrentMap<String, MethodHandle> methodHandleCache = new ConcurrentHashMap<>();

    /**
     * 使用反射调用目标对象上的方法
     *
     * @param targetObject 目标对象
     * @param methodName   方法名
     * @param args         方法参数
     * @return 方法的返回值
     * @throws InvocationTargetException 如果底层方法抛出异常
     * @throws IllegalAccessException    如果此 Method 对象强制执行 Java 语言访问控制并且底层方法不可访问
     */
    public static Object invokeMethod(Object targetObject, String methodName, Object... args) throws InvocationTargetException, IllegalAccessException {
        // 获取目标对象的类
        Class<?> targetClazz = targetObject.getClass();
        // 生成缓存键
        String key = getMethodCacheKey(targetClazz, methodName, args);

        // 从缓存中获取方法对象或查找并缓存方法对象
        Method methodHandle = methodCache.computeIfAbsent(key, k -> findAndCacheMethod(targetClazz, methodName, args));
        // 使用方法对象调用目标方法
        return methodHandle.invoke(targetObject, args);
    }

    /**
     * 生成方法缓存的键
     *
     * @param clazz      目标类
     * @param methodName 方法名
     * @param args       方法参数
     * @return 缓存键
     */
    private static String getMethodCacheKey(Class<?> clazz, String methodName, Object... args) {
        // 生成唯一的缓存键，包含类名、方法名和参数类型信息
        StringBuilder key = new StringBuilder(clazz.getCanonicalName()).append('.').append(methodName);
        if (args != null) {
            for (Object arg : args) {
                key.append('.').append(arg != null ? arg.getClass().getCanonicalName() : "null");
            }
        }
        return key.toString();
    }

    /**
     * 在目标类中查找并缓存符合的方法
     *
     * @param targetClazz 目标类
     * @param methodName  方法名
     * @param args        方法参数
     * @return 匹配的方法
     * @throws RuntimeException 如果没有找到匹配的方法
     */
    private static Method findAndCacheMethod(Class<?> targetClazz, String methodName, Object... args) {
        // 获取参数类型数组
        Class<?>[] parameterTypes = getParameterTypes(args);
        try {
            // 尝试精确匹配方法
            return targetClazz.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            // 如果精确匹配失败，尝试宽松匹配
            return Arrays.stream(targetClazz.getMethods())
                    .filter(m -> m.getName().equals(methodName) && isAssignableFrom(m.getParameterTypes(), parameterTypes))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException(e));
        }
    }

    /**
     * 获取参数类型数组
     *
     * @param args 方法参数
     * @return 参数类型数组
     */
    private static Class<?>[] getParameterTypes(Object... args) {
        if (args == null) {
            return new Class<?>[0];
        }
        // 将参数转换为参数类型数组，处理参数为null的情况
        return Arrays.stream(args)
                .map(arg -> arg != null ? arg.getClass() : Object.class)
                .toArray(Class<?>[]::new);
    }

    /**
     * 检查参数类型是否可以分配给方法参数类型
     *
     * @param methodParameterTypes 方法参数类型数组
     * @param parameterTypes       实际参数类型数组
     * @return 如果参数类型匹配，则返回true；否则返回false
     */
    private static boolean isAssignableFrom(Class<?>[] methodParameterTypes, Class<?>[] parameterTypes) {
        if (methodParameterTypes.length != parameterTypes.length) {
            return false;
        }
        for (int i = 0; i < methodParameterTypes.length; i++) {
            if (!methodParameterTypes[i].isAssignableFrom(parameterTypes[i]) && parameterTypes[i] != Object.class) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取带有指定注解的接口类型
     *
     * @param targetClass     目标类
     * @param annotationClass 注解类
     * @param <A>             注解类型
     * @return 带有指定注解的接口类型
     */
    public static <A extends Annotation> Class<A> getInterfaceByAnnotation(Class<?> targetClass, Class<A> annotationClass) {
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
                    if (interfaceClass.isInterface() && interfaceClass.getAnnotation(annotationClass) != null) {
                        return (Class<A>) interfaceClass;
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
     * 使用 MethodHandle 调用目标对象上的方法
     *
     * @param object     目标对象
     * @param methodName 方法名
     * @param args       方法参数
     * @return 方法的返回值
     * @throws Throwable 如果方法调用失败
     */
    public static Object invokeVirtualMethod(Object object, String methodName, Object... args) throws Throwable {
        // 生成缓存键
        String key = getMethodCacheKey(object.getClass(), methodName, args);

        // 从缓存中获取MethodHandle对象或查找并缓存MethodHandle对象
        MethodHandle methodHandle = methodHandleCache.computeIfAbsent(key, k -> {
            Class<?> clazz = object.getClass();
            Class<?>[] parameterTypes = getParameterTypes(args);

            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodType methodType = MethodType.methodType(Object.class, parameterTypes);
            try {
                return lookup.findVirtual(clazz, methodName, methodType).bindTo(object);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });
        // 使用MethodHandle对象调用目标方法
        return methodHandle.invokeWithArguments(args);
    }

    /**
     * 使用反射创建一个新的实例
     *
     * @param clazz          要实例化的类
     * @param parameterTypes 构造函数的参数类型
     * @param args           构造函数的参数
     * @return 新创建的实例
     */
    public static Object newInstance(Class<?> clazz, Class<?>[] parameterTypes, Object[] args) {
        try {
            return clazz.getConstructor(parameterTypes).newInstance(args);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 通过反射获取对象指定字段的值，支持向上查找父类字段。
     *
     * @param entity    要读取字段的对象
     * @param fieldName 字段名
     * @return 字段值
     */
    public static Object getFieldValue(Object entity, String fieldName) throws NoSuchFieldException, IllegalAccessException {
        if (entity == null) {
            throw new IllegalArgumentException("Cannot read field from null object");
        }
        Field field = findField(entity.getClass(), fieldName);
        field.setAccessible(true);
        return field.get(entity);
    }

    /**
     * 在类及其父类中查找指定名称的字段。
     *
     * @param clazz     起始类
     * @param fieldName 字段名
     * @return 找到的 Field 对象
     * @throws NoSuchFieldException 如果在类层次中未找到字段
     */
    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field '" + fieldName + "' not found in " + clazz.getName() + " or its superclasses");
    }
}