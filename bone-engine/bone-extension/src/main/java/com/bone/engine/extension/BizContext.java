package com.bone.engine.extension;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 业务上下文类
 * <p>
 * 提供线程安全的业务维度管理和属性扩展机制，是扩展点框架中传递业务信息的核心容器
 * 支持标准业务维度（租户、业务、用例、场景）和自定义扩展属性，用于路由决策和业务数据传递
 * </p>
 * 
 * <h3>主要功能：</h3>
 * <ul>
 *   <li>封装标准业务维度信息</li>
 *   <li>提供线程安全的属性存储和访问</li>
 *   <li>支持链式调用的流畅API</li>
 *   <li>提供构建器模式创建实例</li>
 *   <li>支持上下文合并和复制</li>
 *   <li>生成用于路由匹配的业务标识</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>
 * {@code
 * // 使用构建器创建上下文
 * BizContext<Order> context = BizContext.<Order>
 *     .builder()
 *     .tenantCode("TENANT_A")
 *     .bizCode("ORDER")
 *     .scenario("PROMOTION")
 *     .data(order)
 *     .attribute("userId", "12345")
 *     .attribute("orderAmount", 999.99)
 *     .build();
 * 
 * // 使用静态工厂方法创建
 * BizContext<String> simpleContext = BizContext.of("TENANT_B", "PAYMENT");
 * 
 * // 使用链式调用添加属性
 * simpleContext.withAttribute("paymentMethod", "CREDIT_CARD")
 *              .withAttribute("currency", "CNY");
 * 
 * // 获取属性值
 * String userId = context.getAttribute("userId");
 * Double amount = context.getAttributeOrDefault("discount", 0.0);
 * }
 * </pre>
 * 
 * @param <T> 业务数据类型，可存储具体的业务对象
 * @see EnableExtPoints 启用扩展点框架
 * @see ExtPoint 扩展点接口标记
 * @see ExtProvider 扩展点提供者标记
 * @since 1.0.0
 */
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class BizContext<T> {
    // 标准业务维度
    @Nullable
    private String tenantCode;  // 租户编码
    @Nullable
    private String bizCode;     // 业务编码
    @Nullable
    private String useCase;     // 用例编码
    @Nullable
    private String scenario;    // 场景编码
    @Nullable
    private T data;             // 业务数据对象
    
    // 扩展属性，使用ConcurrentHashMap保证线程安全
    @Nullable
    private Map<String, Object> attributes;

    /**
     * 创建空的业务上下文实例
     * 
     * @param <T> 业务数据类型
     * @return 新的空业务上下文实例
     */
    public static <T> BizContext<T> create() {
        return new BizContext<>();
    }

    /**
     * 使用租户编码创建业务上下文
     * 
     * @param tenantCode 租户编码
     * @param <T> 业务数据类型
     * @return 新的业务上下文实例
     */
    public static <T> BizContext<T> ofTenant(String tenantCode) {
        BizContext<T> context = new BizContext<>();
        context.setTenantCode(tenantCode);
        return context;
    }

    /**
     * 使用业务编码创建业务上下文
     * 
     * @param bizCode 业务编码
     * @param <T> 业务数据类型
     * @return 新的业务上下文实例
     */
    public static <T> BizContext<T> ofBusiness(String bizCode) {
        BizContext<T> context = new BizContext<>();
        context.setBizCode(bizCode);
        return context;
    }

    /**
     * 使用租户和业务编码创建业务上下文
     * 
     * @param tenantCode 租户编码
     * @param bizCode 业务编码
     * @param <T> 业务数据类型
     * @return 新的业务上下文实例
     */
    public static <T> BizContext<T> of(String tenantCode, String bizCode) {
        BizContext<T> context = new BizContext<>();
        context.setTenantCode(tenantCode);
        context.setBizCode(bizCode);
        return context;
    }

    /**
     * 生成业务标识字符串，用于扩展点路由匹配
     * <p>
     * 标识格式：tenantCode/bizCode/useCase/scenario
     * 使用默认值(*)表示未指定的维度
     * </p>
     * 
     * @return 业务标识字符串
     */
    public String getBusinessIdentity() {
        return buildIdentity(tenantCode, bizCode, useCase, scenario);
    }

    /**
     * 生成默认业务标识字符串（所有维度使用默认值）
     * 
     * @return 默认业务标识字符串
     */
    public String getDefaultBusinessIdentity() {
        return buildIdentity(null, null, null, null);
    }

    /**
     * 构建业务标识字符串
     * 
     * @param tenant 租户编码
     * @param business 业务编码
     * @param use 用例编码
     * @param scene 场景编码
     * @return 业务标识字符串
     */
    private String buildIdentity(String tenant, String business, String use, String scene) {
        return getValueOrDefault(tenant) + ExtPointConstants.SEPARATOR
                + getValueOrDefault(business) + ExtPointConstants.SEPARATOR
                + getValueOrDefault(use) + ExtPointConstants.SEPARATOR
                + getValueOrDefault(scene);
    }

    /**
     * 获取值或默认值
     * 
     * @param value 原始值
     * @return 非空值或默认值(*)
     */
    private String getValueOrDefault(String value) {
        return StringUtils.hasText(value) ? value : ExtPointConstants.DEFAULT_VALUE;
    }

    /**
     * 检查上下文是否有效（至少包含租户或业务标识）
     * 
     * @return 上下文是否有效
     */
    public boolean isValid() {
        return StringUtils.hasText(tenantCode) || StringUtils.hasText(bizCode);
    }

    /**
     * 添加扩展属性（线程安全）
     * 
     * @param key 属性键名，不能为空
     * @param value 属性值，可为null
     * @return 当前上下文实例，支持链式调用
     */
    public BizContext<T> withAttribute(String key, @Nullable Object value) {
        Objects.requireNonNull(key, "Attribute key must not be null");
        
        if (attributes == null) {
            attributes = new ConcurrentHashMap<>();
        }
        attributes.put(key, value);
        return this;
    }

    /**
     * 获取扩展属性值
     * 
     * @param key 属性键名
     * @param <V> 属性值类型
     * @return 属性值，如果不存在则返回null
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <V> V getAttribute(String key) {
        Objects.requireNonNull(key, "Attribute key must not be null");
        
        if (attributes == null) {
            return null;
        }
        return (V) attributes.get(key);
    }

    /**
     * 获取扩展属性值，如果不存在则返回默认值
     * 
     * @param key 属性键名
     * @param defaultValue 默认值
     * @param <V> 属性值类型
     * @return 属性值或默认值
     */
    @SuppressWarnings("unchecked")
    public <V> V getAttributeOrDefault(String key, V defaultValue) {
        Objects.requireNonNull(key, "Attribute key must not be null");
        
        if (attributes == null) {
            return defaultValue;
        }
        return (V) attributes.getOrDefault(key, defaultValue);
    }

    /**
     * 如果属性不存在，则使用提供的函数计算并设置属性值
     * <p>
     * 适用于属性值需要延迟计算的场景，避免不必要的计算开销
     * </p>
     * 
     * @param key 属性键名
     * @param mappingFunction 属性值计算函数
     * @param <V> 属性值类型
     * @return 属性值
     */
    @SuppressWarnings("unchecked")
    public <V> V computeAttributeIfAbsent(String key, Function<String, V> mappingFunction) {
        Objects.requireNonNull(key, "Attribute key must not be null");
        Objects.requireNonNull(mappingFunction, "Mapping function must not be null");
        
        if (attributes == null) {
            attributes = new ConcurrentHashMap<>();
        }
        return (V) attributes.computeIfAbsent(key, mappingFunction);
    }

    /**
     * 检查是否包含指定的扩展属性
     * 
     * @param key 属性键名
     * @return 是否包含该属性
     */
    public boolean hasAttribute(String key) {
        Objects.requireNonNull(key, "Attribute key must not be null");
        return attributes != null && attributes.containsKey(key);
    }
    
    /**
     * 检查是否包含指定的扩展属性（兼容方法）
     * 
     * @param key 属性键名
     * @return 是否包含该属性
     */
    public boolean containsAttribute(String key) {
        return hasAttribute(key);
    }

    /**
     * 移除指定的扩展属性
     * 
     * @param key 属性键名
     * @param <V> 属性值类型
     * @return 被移除的属性值，如果不存在则返回null
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <V> V removeAttribute(String key) {
        Objects.requireNonNull(key, "Attribute key must not be null");
        
        if (attributes == null) {
            return null;
        }
        return (V) attributes.remove(key);
    }

    /**
     * 获取所有扩展属性的副本
     * <p>
     * 返回的是一个新的Map实例，修改不会影响原上下文
     * </p>
     * 
     * @return 扩展属性映射的副本
     */
    public Map<String, Object> getAllAttributes() {
        if (attributes == null) {
            return new ConcurrentHashMap<>();
        }
        return new ConcurrentHashMap<>(attributes);
    }

    /**
     * 设置所有扩展属性
     * 
     * @param attributes 要设置的属性映射
     * @return 当前上下文实例，支持链式调用
     */
    public BizContext<T> withAttributes(@Nullable Map<String, Object> attributes) {
        this.attributes = attributes != null ? new ConcurrentHashMap<>(attributes) : null;
        return this;
    }

    /**
     * 合并另一个上下文中的属性到当前上下文
     * <p>
     * 标准维度仅在当前值为空时才被覆盖
     * 扩展属性会被直接合并，如果有相同键则覆盖
     * </p>
     * 
     * @param other 要合并的上下文
     * @return 当前上下文实例，支持链式调用
     */
    public BizContext<T> merge(@Nullable BizContext<?> other) {
        if (other == null) {
            return this;
        }
        
        // 合并标准维度（仅当当前值为空时）
        if (!StringUtils.hasText(tenantCode) && StringUtils.hasText(other.tenantCode)) {
            this.tenantCode = other.tenantCode;
        }
        if (!StringUtils.hasText(bizCode) && StringUtils.hasText(other.bizCode)) {
            this.bizCode = other.bizCode;
        }
        if (!StringUtils.hasText(useCase) && StringUtils.hasText(other.useCase)) {
            this.useCase = other.useCase;
        }
        if (!StringUtils.hasText(scenario) && StringUtils.hasText(other.scenario)) {
            this.scenario = other.scenario;
        }
        
        // 合并扩展属性
        if (other.attributes != null && !other.attributes.isEmpty()) {
            if (this.attributes == null) {
                this.attributes = new ConcurrentHashMap<>();
            }
            this.attributes.putAll(other.attributes);
        }
        
        return this;
    }

    /**
     * 创建当前上下文的深拷贝
     * <p>
     * 返回的副本与原上下文完全独立，修改不会相互影响
     * </p>
     * 
     * @return 上下文副本
     */
    public BizContext<T> copy() {
        // 直接创建新实例并复制所有属性
        BizContext<T> copy = new BizContext<>();
        copy.tenantCode = this.tenantCode;
        copy.bizCode = this.bizCode;
        copy.useCase = this.useCase;
        copy.scenario = this.scenario;
        copy.data = this.data;
        copy.attributes = attributes != null ? new ConcurrentHashMap<>(attributes) : null;
        return copy;
    }

    /**
     * 构建器内部类，提供流畅的API创建BizContext实例
     */
    public static class Builder<T> {
        @Nullable
        private Map<String, Object> attributes;
        
        /**
         * 添加扩展属性
         * 
         * @param key 属性键名，不能为空
         * @param value 属性值，可为null
         * @return 当前构建器实例
         */
        public Builder<T> attribute(String key, @Nullable Object value) {
            Objects.requireNonNull(key, "Attribute key must not be null");
            
            if (attributes == null) {
                attributes = new ConcurrentHashMap<>();
            }
            attributes.put(key, value);
            return this;
        }

        /**
         * 添加多个扩展属性
         * 
         * @param attrs 属性映射
         * @return 当前构建器实例
         */
        public Builder<T> attributes(@Nullable Map<String, Object> attrs) {
            if (attrs != null) {
                if (attributes == null) {
                    attributes = new ConcurrentHashMap<>();
                }
                attributes.putAll(attrs);
            }
            return this;
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BizContext<?> that = (BizContext<?>) o;
        return Objects.equals(tenantCode, that.tenantCode) &&
               Objects.equals(bizCode, that.bizCode) &&
               Objects.equals(useCase, that.useCase) &&
               Objects.equals(scenario, that.scenario);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(tenantCode, bizCode, useCase, scenario);
    }
    
    /**
     * 获取业务标识，用于路由匹配
     * 
     * @return 业务标识字符串
     */
    public String getBizIdentity() {
        StringBuilder sb = new StringBuilder();
        sb.append(StringUtils.hasText(tenantCode) ? tenantCode : "DEFAULT");
        sb.append("|");
        sb.append(StringUtils.hasText(bizCode) ? bizCode : "DEFAULT");
        sb.append("|");
        sb.append(StringUtils.hasText(useCase) ? useCase : "DEFAULT");
        sb.append("|");
        sb.append(StringUtils.hasText(scenario) ? scenario : "DEFAULT");
        return sb.toString();
    }
}