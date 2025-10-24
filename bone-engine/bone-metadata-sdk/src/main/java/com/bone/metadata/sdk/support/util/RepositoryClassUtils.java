package com.bone.metadata.sdk.support.util;

import com.bone.metadata.sdk.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 类工具类，提供通用的类操作功能
 */
public class RepositoryClassUtils {
    private static final Logger logger = LoggerFactory.getLogger(RepositoryClassUtils.class);
    private static final Map<String, Class<?>[]> GENERIC_CACHE = new ConcurrentHashMap<>();

    /**
     * 判断类型是否为简单类型
     * @param type 要检查的类型
     * @return 如果是简单类型返回true，否则返回false
     */
    public static boolean isSimpleType(Class<?> type) {
        return type.isPrimitive() ||
                Number.class.isAssignableFrom(type) ||
                CharSequence.class.isAssignableFrom(type) ||
                Boolean.class.equals(type) ||
                java.util.Date.class.isAssignableFrom(type) ||
                java.time.temporal.Temporal.class.isAssignableFrom(type) ||
                type == Object.class;
    }

    /**
     * 解析Repository接口的泛型参数类型
     * @param repoInterface Repository接口类
     * @return 包含实体类和ID类的数组，如果解析失败返回null
     */
    public static Class<?>[] resolveGenericTypes(Class<?> repoInterface) {
        // 使用缓存避免重复解析
        return GENERIC_CACHE.computeIfAbsent(repoInterface.getName(), key -> {
            // 检查直接实现的泛型接口
            for (Type genericInterface : repoInterface.getGenericInterfaces()) {
                if (genericInterface instanceof ParameterizedType pt) {
                    if (pt.getRawType() instanceof Class<?> rawType && 
                            Repository.class.isAssignableFrom(rawType)) {
                        Type[] actualTypes = pt.getActualTypeArguments();
                        if (actualTypes.length >= 2) {
                            try {
                                // 处理实际类型参数
                                if (actualTypes[0] instanceof Class<?> entityClass && 
                                        actualTypes[1] instanceof Class<?> idClass) {
                                    return new Class<?>[]{entityClass, idClass};
                                } else {
                                    // 尝试通过类名加载
                                    Class<?> entityClass = Class.forName(actualTypes[0].getTypeName());
                                    Class<?> idClass = Class.forName(actualTypes[1].getTypeName());
                                    return new Class<?>[]{entityClass, idClass};
                                }
                            } catch (ClassNotFoundException e) {
                                logger.error("Failed to resolve generic types for {}", repoInterface.getName(), e);
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

            return null;
        });
    }
    
    /**
     * 检查接口是否直接或间接实现了Repository接口
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
}