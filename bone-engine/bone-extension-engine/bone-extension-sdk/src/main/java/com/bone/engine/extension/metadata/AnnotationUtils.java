package com.bone.engine.extension.metadata;

import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * 注解工具类
 * <p>
 * 提供扩展点相关注解的便捷处理方法
 * </p>
 * 
 * @since 1.0.0
 */
public class AnnotationUtils {
    
    private static final Logger log = LoggerFactory.getLogger(AnnotationUtils.class);
    
    /**
     * 从类中提取Extension注解的元数据
     * 
     * @param clazz 实现类
     * @return Extension注解的元数据映射
     */
    public static Map<String, Object> extractExtensionMetadata(Class<?> clazz) {
        Extension extension = clazz.getAnnotation(Extension.class);
        if (extension == null) {
            return null;
        }
        
        Map<String, Object> metadata = new HashMap<>();
        
        // 提取注解属性 - 只保留基本信息
        try {
            // 基本路由信息
            metadata.put("tenantCode", extension.tenantCode());
            metadata.put("bizCode", extension.bizCode());
            metadata.put("useCase", extension.useCase());
            metadata.put("scenario", extension.scenario());
            
            // 基本元数据属性
            metadata.put("description", extension.description());
            metadata.put("priority", extension.priority());
            metadata.put("isDefault", extension.isDefault());
            
        } catch (Exception e) {
            log.warn("Error extracting extension metadata from " + clazz.getName() + ": " + e.getMessage());
        }
        
        return metadata;
    }
    
    /**
     * 从接口中提取ExtPoint注解的元数据
     * 
     * @param interfaceClazz 扩展点接口
     * @return ExtPoint注解的元数据映射
     */
    public static Map<String, Object> extractExtPointMetadata(Class<?> interfaceClazz) {
        if (interfaceClazz == null || !interfaceClazz.isInterface()) {
            return null;
        }
        
        ExtPoint extPoint = interfaceClazz.getAnnotation(ExtPoint.class);
        if (extPoint == null) {
            return null;
        }
        
        Map<String, Object> metadata = new HashMap<>();
        
        try {
            // 只保留基本信息
            metadata.put("description", extPoint.description());
            // 移除对deprecated()方法的调用，因为ExtPoint接口中没有这个方法
            
        } catch (Exception e) {
            log.warn("Error extracting extPoint metadata from " + interfaceClazz.getName() + ": " + e.getMessage());
        }
        
        return metadata;
    }
    
    /**
     * 获取注解的属性值
     * 
     * @param annotation 注解对象
     * @param attributeName 属性名
     * @return 属性值
     */
    public static Object getAnnotationAttribute(Annotation annotation, String attributeName) {
        try {
            Method method = annotation.annotationType().getMethod(attributeName);
            method.setAccessible(true);
            return method.invoke(annotation);
        } catch (Exception e) {
            log.warn("Failed to get attribute " + attributeName + " from annotation " + annotation.annotationType().getName() + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 检查类是否标记为默认扩展实现
     * 
     * @param clazz 实现类
     * @return 是否为默认实现
     */
    public static boolean isDefaultImplementation(Class<?> clazz) {
        Extension extension = clazz.getAnnotation(Extension.class);
        return extension != null && extension.isDefault();
    }
    
    /**
     * 获取扩展实现的优先级
     * 
     * @param clazz 实现类
     * @return 优先级值
     */
    public static int getImplementationPriority(Class<?> clazz) {
        Extension extension = clazz.getAnnotation(Extension.class);
        return extension != null ? extension.priority() : 0;
    }
    
    /**
     * 检查类是否标记为推荐实现
     * 
     * @param clazz 实现类
     * @return 是否为推荐实现
     */
    public static boolean isRecommendedImplementation(Class<?> clazz) {
        Extension extension = clazz.getAnnotation(Extension.class);
        // 使用isDefault属性作为推荐实现的判断依据
        return extension != null && extension.isDefault();
    }
    
    /**
     * 检查注解元素是否包含指定注解
     * 
     * @param element 注解元素
     * @param annotationType 注解类型
     * @return 是否包含
     */
    public static boolean hasAnnotation(AnnotatedElement element, Class<? extends Annotation> annotationType) {
        return element != null && element.isAnnotationPresent(annotationType);
    }
}