package com.bone.metadata.sdk.support.util;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

/**
 * 参数转换工具类：高效将JavaBean转换为查询参数Map，过滤null值和空白字符串
 * 线程安全，高性能设计，适合高频调用场景
 */
@Slf4j
public final class ParamConvertUtil {

    /**
     * 线程安全的字段元数据缓存：Class -> 字段数组
     */
    private static final ConcurrentMap<Class<?>, Field[]> FIELD_CACHE = new ConcurrentHashMap<>(128);

    /**
     * ✅ 新增：线程安全的类加载缓存：类名 -> Class 对象
     * 使用 ConcurrentHashMap 保证高并发性能
     */
    private static final ConcurrentMap<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>(64);

    /**
     * 空白字符串检查正则（预编译提升性能）：匹配全空白字符
     */
    private static final Pattern BLANK_PATTERN = Pattern.compile("\\s*");

    /**
     * 私有构造函数：防止工具类被实例化
     */
    private ParamConvertUtil() {
        throw new AssertionError("工具类不允许实例化");
    }

    // -------------------- 新增方法：高性能类加载 --------------------

    /**
     * 根据类名高效加载并缓存 Class 对象。
     * 线程安全，首次加载后缓存，后续直接返回。
     *
     * @param className 全限定类名，如 "com.example.MyClass"
     * @param <T>       期望的类型
     * @return 加载的 Class 对象
     * @throws IllegalArgumentException 如果类不存在或加载失败
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> loadClass(String className) {
        if (className == null || className.trim().isEmpty()) {
            throw new IllegalArgumentException("类名不能为空");
        }
        className = className.trim();

        // 🔥 核心：使用 computeIfAbsent 实现无锁线程安全缓存
        return (Class<T>) CLASS_CACHE.computeIfAbsent(className, name -> {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                log.error("类加载失败: {}", name, e);
                // ❌ 不建议在此处抛出受检异常，因为 computeIfAbsent 不允许
                // 我们将异常包装并抛出运行时异常，由 computeIfAbsent 捕获并作为缓存值？不！
                // 正确做法：抛出 RuntimeException，computeIfAbsent 会中断并让外层捕获
                throw new IllegalStateException("无法加载类: " + name, e);
            }
        });
    }

    /**
     * 根据类名加载 Class，但允许调用者处理 ClassNotFoundException。
     * 适用于需要精确控制异常处理的场景。
     *
     * @param className 全限定类名
     * @param <T>       期望的类型
     * @return Class 对象
     * @throws ClassNotFoundException 如果类不存在
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> loadClassWithException(String className) throws ClassNotFoundException {
        if (className == null || className.trim().isEmpty()) {
            throw new ClassNotFoundException("类名不能为空");
        }
        className = className.trim();

        // 先尝试从缓存获取
        Class<?> cached = CLASS_CACHE.get(className);
        if (cached != null) {
            return (Class<T>) cached;
        }

        // 缓存未命中，同步加载并放入缓存
        synchronized (ParamConvertUtil.class) { // 类锁，粒度较大但安全
            cached = CLASS_CACHE.get(className);
            if (cached == null) {
                try {
                    cached = Class.forName(className);
                    CLASS_CACHE.put(className, cached);
                } catch (ClassNotFoundException e) {
                    throw e;
                }
            }
        }

        return (Class<T>) (cached);
    }


    // -------------------- 原有方法 --------------------

    /**
     * 将JavaBean转换为参数Map
     * 规则：过滤null值和空白字符串，键为字段名，值为字段原值
     *
     * @param paramBean 待转换的参数Bean（非null）
     * @return 处理后的参数Map
     */
    public static Map<String, Object> toParamMap(Object paramBean) {
        if (paramBean == null) {
            return Collections.emptyMap();
        }

        Class<?> beanClass = paramBean.getClass();
        Field[] fields = getFields(beanClass);

        // 预分配容量：避免HashMap动态扩容
        Map<String, Object> paramMap = new HashMap<>(fields.length);

        for (Field field : fields) {
            try {
                Object fieldValue = field.get(paramBean);
                // 过滤null值
                if (fieldValue == null) {
                    continue;
                }
                // 处理普通字段：过滤空白字符串
                if (processField(fieldValue, field, paramMap)) {
                    continue;
                }
            } catch (IllegalAccessException e) {
                // 此处静默处理：跳过无法访问的字段
                log.error("字段访问异常: " + field.getName(), e);
            }
        }

        return paramMap;
    }

    /**
     * 获取类的字段数组（带缓存）
     * 首次调用解析类结构，后续直接从缓存获取
     */
    private static Field[] getFields(Class<?> clazz) {
        return FIELD_CACHE.computeIfAbsent(clazz, ParamConvertUtil::resolveFields);
    }

    /**
     * 解析类的字段数组（包括父类字段）
     * 过滤静态字段，设置字段可访问
     */
    private static Field[] resolveFields(Class<?> clazz) {
        List<Field> fieldList = new ArrayList<>();
        Class<?> currentClass = clazz;

        // 遍历类及其所有父类（终止于Object类）
        while (currentClass != null && currentClass != Object.class) {
            Field[] declaredFields = currentClass.getDeclaredFields();
            for (Field field : declaredFields) {
                // 跳过静态字段
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                // 设置字段可访问（突破private修饰符限制）
                field.setAccessible(true);
                fieldList.add(field);
            }
            currentClass = currentClass.getSuperclass();
        }

        return fieldList.toArray(new Field[0]);
    }

    /**
     * 处理普通字段：过滤空白字符串，添加有效字段到Map
     *
     * @return 是否成功添加
     */
    private static boolean processField(Object fieldValue, Field field, Map<String, Object> paramMap) {
        // 字符串类型需检查空白
        if (fieldValue instanceof String strValue) {
            if (isBlank(strValue)) {
                return false; // 空白字符串不添加
            }
            // 保留trim后的字符串（移除首尾空白）
            paramMap.put(field.getName(), strValue.trim());
            return true;
        }
        // 非字符串类型直接添加（已过滤null）
        paramMap.put(field.getName(), fieldValue);
        return true;
    }

    /**
     * 高效检查空白字符串
     */
    private static boolean isBlank(String value) {
        return value == null || BLANK_PATTERN.matcher(value).matches();
    }

    /**
     * 清理字段缓存（用于特殊场景：如类热部署后刷新缓存）
     */
    public static void clearCache() {
        FIELD_CACHE.clear();
        CLASS_CACHE.clear(); // ✅ 新增：清理类缓存
    }

    /**
     * 获取当前缓存大小（用于监控和调试）
     */
    public static int getCacheSize() {
        return FIELD_CACHE.size();
    }

    /**
     * ✅ 新增：获取类缓存大小
     */
    public static int getClassCacheSize() {
        return CLASS_CACHE.size();
    }

    /**
     * ✅ 新增：清理类缓存
     */
    public static void clearClassCache() {
        CLASS_CACHE.clear();
    }
}