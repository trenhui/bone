package com.bone.engine.extension;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.bone.engine.extension.extractor.BizParamExtractor;
import com.bone.engine.extension.extractor.BizParamExtractorFactory;
import com.bone.engine.extension.extractor.ReflectionBizParamExtractor;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.Objects;

/**
 * 业务上下文工具类，提供线程安全的业务上下文管理机制
 * <p>
 * 基于TransmittableThreadLocal实现，确保在线程池环境下上下文能够正确传递
 * 支持try-with-resources模式，自动管理上下文的生命周期
 *
 * @author renhui.trh 2023-11-1
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BizContexts {

    // 线程本地存储，保存当前业务上下文
    private static final TransmittableThreadLocal<BizContext<?>> CONTEXT_HOLDER = new TransmittableThreadLocal<>();

    /**
     * 获取当前业务上下文
     * 
     * @return 当前业务上下文，如果不存在则返回null
     */
    @Nullable
    public static BizContext<?> getCurrent() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 获取当前业务上下文，如果不存在则抛出异常
     * 
     * @return 当前业务上下文
     * @throws IllegalStateException 如果上下文不存在
     */
    public static BizContext<?> getRequiredCurrent() {
        BizContext<?> context = getCurrent();
        if (context == null) {
            throw new IllegalStateException("No business context found in current thread");
        }
        return context;
    }

    /**
     * 创建并设置业务上下文，返回上下文管理器（支持try-with-resources）
     * 
     * @param context 业务上下文
     * @return 上下文管理器
     */
    public static ContextManager with(BizContext<?> context) {
        Objects.requireNonNull(context, "Business context must not be null");
        
        // 解析上下文参数
        enrichContext(context);
        
        // 保存旧上下文
        BizContext<?> oldContext = CONTEXT_HOLDER.get();
        
        // 设置新上下文
        CONTEXT_HOLDER.set(context);
        log.debug("Business context set: {}", context.getBizIdentity());
        
        // 返回上下文管理器，用于自动清理
        return () -> {
            CONTEXT_HOLDER.remove();
            // 恢复旧上下文（如果有）
            if (oldContext != null) {
                CONTEXT_HOLDER.set(oldContext);
                log.debug("Restored previous business context: {}", oldContext.getBizIdentity());
            } else {
                log.debug("Business context cleared");
            }
        };
    }

    /**
     * 使用租户编码创建上下文
     * 
     * @param tenantCode 租户编码
     * @return 上下文管理器
     */
    public static ContextManager withTenant(String tenantCode) {
        return with(BizContext.ofTenant(tenantCode));
    }

    /**
     * 使用业务编码创建上下文
     * 
     * @param bizCode 业务编码
     * @return 上下文管理器
     */
    public static ContextManager withBusiness(String bizCode) {
        return with(BizContext.ofBusiness(bizCode));
    }

    /**
     * 使用租户和业务编码创建上下文
     * 
     * @param tenantCode 租户编码
     * @param bizCode 业务编码
     * @return 上下文管理器
     */
    public static ContextManager with(String tenantCode, String bizCode) {
        return with(BizContext.of(tenantCode, bizCode));
    }

    /**
     * 从HTTP请求构建上下文
     * 
     * @return 构建的上下文，如果没有请求则返回null
     */
    @Nullable
    public static BizContext<?> fromRequest() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }

        // 从请求参数和路径变量中提取维度信息
        String tenantCode = extractDimension(request, "tenantCode");
        String bizCode = extractDimension(request, "bizCode");
        String useCase = extractDimension(request, "useCase");
        String scenario = extractDimension(request, "scenario");

        return BizContext.builder()
                .tenantCode(tenantCode)
                .bizCode(bizCode)
                .useCase(useCase)
                .scenario(scenario)
                .build();
    }

    /**
     * 从数据对象构建上下文
     * 
     * @param data 数据对象
     * @return 构建的上下文
     */
    public static <T> BizContext<T> fromData(T data) {
        Objects.requireNonNull(data, "Data object must not be null");
        
        BizContext<T> context = BizContext.<T>builder().data(data).build();
        enrichFromData(context);
        return context;
    }

    /**
     * 复制当前上下文到新线程
     * 
     * @return 上下文复制器
     */
    public static ContextCopier copy() {
        final BizContext<?> original = getCurrent();
        return () -> {
            if (original != null) {
                CONTEXT_HOLDER.set(original.copy());
            }
        };
    }

    /**
     * 清除当前线程的上下文
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
        log.debug("Business context cleared");
    }

    /**
     * 检查当前线程是否有上下文
     * 
     * @return 是否有上下文
     */
    public static boolean hasContext() {
        return CONTEXT_HOLDER.get() != null;
    }

    /**
     * 丰富上下文信息
     */
    private static void enrichContext(BizContext<?> context) {
        Objects.requireNonNull(context, "Context must not be null");
        
        // 首先尝试从HTTP请求中获取信息
        HttpServletRequest request = getRequest();
        if (request != null) {
            enrichFromRequest(context, request);
        }
        
        // 然后尝试从数据对象中获取信息
        if (context.getData() != null) {
            enrichFromData(context);
        }
    }

    /**
     * 从HTTP请求丰富上下文
     */
    private static void enrichFromRequest(BizContext<?> context, HttpServletRequest request) {
        if (StringUtils.isBlank(context.getTenantCode())) {
            context.setTenantCode(extractDimension(request, "tenantCode"));
        }
        if (StringUtils.isBlank(context.getBizCode())) {
            context.setBizCode(extractDimension(request, "bizCode"));
        }
        if (StringUtils.isBlank(context.getUseCase())) {
            context.setUseCase(extractDimension(request, "useCase"));
        }
        if (StringUtils.isBlank(context.getScenario())) {
            context.setScenario(extractDimension(request, "scenario"));
        }
    }

    /**
     * 从数据对象丰富上下文
     */
    @SuppressWarnings("unchecked")
    private static <T> void enrichFromData(BizContext<T> context) {
        T data = context.getData();
        if (data == null) {
            return;
        }

        BizParamExtractor<T> extractor = (BizParamExtractor<T>) BizParamExtractorFactory.getExtractor(data);
        if (extractor == null) {
            extractor = new ReflectionBizParamExtractor<>();
        }

        if (StringUtils.isBlank(context.getTenantCode())) {
            context.setTenantCode(extractor.getTenantCode(data));
        }
        if (StringUtils.isBlank(context.getBizCode())) {
            context.setBizCode(extractor.getBizCode(data));
        }
        if (StringUtils.isBlank(context.getUseCase())) {
            context.setUseCase(extractor.getUseCase(data));
        }
        if (StringUtils.isBlank(context.getScenario())) {
            context.setScenario(extractor.getScenario(data));
        }
    }

    /**
     * 提取维度信息
     */
    private static String extractDimension(HttpServletRequest request, String paramName) {
        // 首先尝试从请求参数中获取
        String value = request.getParameter(paramName);
        
        // 如果参数不存在，尝试从路径变量中获取
        if (StringUtils.isBlank(value)) {
            Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(
                    HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE
            );
            if (pathVariables != null) {
                value = pathVariables.get(paramName);
            }
        }
        
        return value;
    }

    /**
     * 获取当前HTTP请求
     */
    @Nullable
    public static HttpServletRequest getRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return null;
        }
        return servletRequestAttributes.getRequest();
    }

    /**
     * 上下文管理器接口，支持try-with-resources模式
     */
    public interface ContextManager extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * 上下文复制器接口，用于在多线程环境中传递上下文
     */
    public interface ContextCopier {
        void apply();
    }

    /**
     * 兼容旧的BizContextUtils类的方法
     */
    @Deprecated
    public static class BizContextUtils {
        public static BizContext getCurrentContext() {
            return (BizContext) BizContexts.getCurrent();
        }
        
        public static void setCurrentContext(BizContext<?> context) {
            if (context != null) {
                CONTEXT_HOLDER.set(context);
            } else {
                CONTEXT_HOLDER.remove();
            }
        }
        
        public static void removeCurrentContext() {
            BizContexts.clear();
        }
        
        public static BizContext getBizContext() {
            return (BizContext) BizContexts.fromRequest();
        }
        
        public static HttpServletRequest getRequest() {
            return BizContexts.getRequest();
        }
        
        public static ContextCopier copyContext() {
            return BizContexts.copy();
        }
    }
}
