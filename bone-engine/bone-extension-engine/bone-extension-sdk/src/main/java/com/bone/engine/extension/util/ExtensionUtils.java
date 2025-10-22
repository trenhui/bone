package com.bone.engine.extension.util;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.Extension;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扩展点工具类
 * <p>
 * 提供扩展点框架相关的通用工具方法
 * <strong>主要功能：</strong>
 * <ul>
 *   <li>注解信息获取</li>
 *   <li>扩展点标识生成</li>
 *   <li>业务上下文处理</li>
 *   <li>类型转换和验证</li>
 * </ul>
 * </p>
 *
 * @author Bone Engine Team
 * @version 1.0.0
 */
public class ExtensionUtils {

    // 扩展点名称缓存
    private static final Map<Class<?>, String> EXT_POINT_NAME_CACHE = new ConcurrentHashMap<>();
    
    // 扩展点实现标识缓存
    private static final Map<Object, String> EXTENSION_ID_CACHE = new ConcurrentHashMap<>();

    /**
     * 获取扩展点名称
     */
    public static String getExtPointName(Class<?> extPointInterface) {
        return EXT_POINT_NAME_CACHE.computeIfAbsent(extPointInterface, clazz -> {
            ExtPoint annotation = AnnotationUtils.getAnnotation(clazz, ExtPoint.class);
            if (annotation != null && StringUtils.hasText(annotation.name())) {
                return annotation.name();
            }
            return clazz.getSimpleName();
        });
    }

    /**
     * 获取扩展点实现标识
     */
    public static String getExtensionId(Object extensionImpl) {
        return EXTENSION_ID_CACHE.computeIfAbsent(extensionImpl, impl -> {
            Extension annotation = AnnotationUtils.getAnnotation(impl.getClass(), Extension.class);
            if (annotation != null && StringUtils.hasText(annotation.name())) {
                return annotation.name();
            }
            return impl.getClass().getSimpleName();
        });
    }

    /**
     * 生成路由键
     */
    public static <T> String generateRouteKey(Class<?> extPointInterface, BizContext<T> context) {
        StringBuilder key = new StringBuilder(getExtPointName(extPointInterface));
        
        if (context != null) {
            key.append(":")
               .append(context.getBizCode() != null ? context.getBizCode() : "default")
               .append(":")
               .append(context.getTenantCode() != null ? context.getTenantCode() : "default")
               .append(":")
               .append(context.getScenario() != null ? context.getScenario() : "default")
               .append(":")
               .append("default")
                .append(":")
               .append("DEFAULT"); // 暂时不调用getGroup()方法
        }
        
        return key.toString();
    }

    /**
     * 检查是否是有效的扩展点接口
     */
    public static boolean isValidExtPointInterface(Class<?> clazz) {
        return clazz != null && clazz.isInterface() && clazz.isAnnotationPresent(ExtPoint.class);
    }

    /**
     * 检查是否是有效的扩展点实现
     */
    public static boolean isValidExtensionImplementation(Object impl) {
        if (impl == null) {
            return false;
        }
        
        Class<?> clazz = impl.getClass();
        if (!clazz.isAnnotationPresent(Extension.class)) {
            return false;
        }
        
        // 检查是否实现了至少一个带@ExtPoint注解的接口
        for (Class<?> ifc : clazz.getInterfaces()) {
            if (ifc.isAnnotationPresent(ExtPoint.class)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 获取扩展点接口的实现类
     */
    public static Class<?> getExtPointInterface(Object extensionImpl) {
        if (extensionImpl == null) {
            return null;
        }
        
        for (Class<?> ifc : extensionImpl.getClass().getInterfaces()) {
            if (ifc.isAnnotationPresent(ExtPoint.class)) {
                return ifc;
            }
        }
        
        return null;
    }

    /**
     * 从方法参数中提取业务上下文
     */
    public static <T> BizContext<T> extractBizContext(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        
        for (Object arg : args) {
            if (arg instanceof BizContext) {
                return (BizContext<T>) arg;
            }
        }
        
        return null;
    }

    /**
     * 确保业务上下文存在
     */
    public static <T> BizContext<T> ensureBizContext(Object[] args) {
        // 尝试从参数中提取BizContext
        BizContext<T> context = extractBizContext(args);
        
        // 如果没有找到上下文，创建一个默认的BizContext
        if (context == null) {
            context = BizContext.createEmpty();
        }
        
        return context;
    }

    /**
     * 生成方法签名字符串
     */
    public static String getMethodSignature(Method method) {
        StringBuilder signature = new StringBuilder(method.getName()).append("(");
        Class<?>[] paramTypes = method.getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            signature.append(paramTypes[i].getSimpleName());
            if (i < paramTypes.length - 1) {
                signature.append(", ");
            }
        }
        signature.append(")");
        return signature.toString();
    }

    /**
     * 检查两个业务上下文是否匹配路由条件
     */
    public static <T, U> boolean matchContext(BizContext<T> context1, BizContext<U> context2) {
        if (context1 == null || context2 == null) {
            return false;
        }
        
        // 业务代码匹配
        if (context1.getBizCode() != null && !context1.getBizCode().equals(context2.getBizCode())) {
            return false;
        }
        
        // 租户代码匹配
        if (context1.getTenantCode() != null && !context1.getTenantCode().equals(context2.getTenantCode())) {
            return false;
        }
        
        // 场景匹配
        if (context1.getScenario() != null && !context1.getScenario().equals(context2.getScenario())) {
            return false;
        }
        
        // 环境和分组匹配暂时注释掉，因为BizContext没有提供对应的getter方法
        // 这两个属性目前不是必需的匹配条件
        
        return true;
    }

    /**
     * 计算路由匹配得分
     */
    public static int calculateMatchScore(Extension extension, BizContext<?> context) {
        int score = 0;
        
        // 检查业务代码匹配
        if (Arrays.asList(extension.bizCode()).contains(context.getBizCode())) {
            score += 100;
        }
        
        // 检查租户代码匹配
        if (Arrays.asList(extension.tenantCode()).contains(context.getTenantCode())) {
            score += 80;
        }
        
        // 检查场景匹配
        if (Arrays.asList(extension.scenario()).contains(context.getScenario())) {
            score += 60;
        }
        
        // 环境和分组匹配暂时注释掉，因为BizContext没有提供对应的getter方法
        // 这两个属性目前不计入匹配分数
        
        // 优先级权重
        score += extension.priority() * 10;
        
        return score;
    }

    /**
     * 清理缓存
     */
    public static void clearCache() {
        EXT_POINT_NAME_CACHE.clear();
        EXTENSION_ID_CACHE.clear();
    }
}