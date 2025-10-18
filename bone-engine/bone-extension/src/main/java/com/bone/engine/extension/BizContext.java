package com.bone.engine.extension;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 业务上下文类，提供线程安全的业务维度管理和属性扩展机制
 * <p>
 * 用于在扩展点执行过程中传递业务相关信息，包含标准维度和自定义属性
 * 支持链式调用、构建器模式和属性继承
 *
 * @param <T> 业务数据类型
 * @author renhui.trh 2023-11-1
 */
@Data
@Builder(toBuilder = true, builderClassName = "Builder")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class BizContext<T> {
    // 标准业务维度
    private String tenantCode;
    private String bizCode;
    private String useCase;
    private String scenario;
    private T data;
    
    // 扩展属性，使用ConcurrentHashMap保证线程安全
    private Map<String, Object> attributes;

    /**
     * 创建空的业务上下文
     * 
     * @return 新的业务上下文实例
     */
    public static <T> BizContext<T> create() {
        return new BizContext<>();
    }

    /**
     * 使用租户编码创建业务上下文
     * 
     * @param tenantCode 租户编码
     * @return 新的业务上下文实例
     */
    public static <T> BizContext<T> ofTenant(String tenantCode) {
        return BizContext.<T>builder().tenantCode(tenantCode).build();
    }

    /**
     * 使用业务编码创建业务上下文
     * 
     * @param bizCode 业务编码
     * @return 新的业务上下文实例
     */
    public static <T> BizContext<T> ofBusiness(String bizCode) {
        return BizContext.<T>builder().bizCode(bizCode).build();
    }

    /**
     * 使用完整维度创建业务上下文
     * 
     * @param tenantCode 租户编码
     * @param bizCode 业务编码
     * @return 新的业务上下文实例
     */
    public static <T> BizContext<T> of(String tenantCode, String bizCode) {
        return BizContext.<T>builder()
                .tenantCode(tenantCode)
                .bizCode(bizCode)
                .build();
    }

    /**
     * 生成业务标识字符串，用于精确匹配
     * 
     * @return 业务标识字符串
     */
    public String getBizIdentity() {
        return buildIdentity(tenantCode, bizCode, useCase, scenario);
    }

    /**
     * 生成默认业务标识字符串
     * 
     * @return 默认业务标识字符串
     */
    public String getDefaultBizIdentity() {
        return buildIdentity(null, null, null, null);
    }

    /**
     * 构建业务标识
     */
    private String buildIdentity(String tenant, String business, String use, String scene) {
        return getValueOrDefault(tenant) + ExtPointConstants.SEPARATOR
                + getValueOrDefault(business) + ExtPointConstants.SEPARATOR
                + getValueOrDefault(use) + ExtPointConstants.SEPARATOR
                + getValueOrDefault(scene);
    }

    /**
     * 获取值或默认值
     */
    private String getValueOrDefault(String value) {
        return StringUtils.hasText(value) ? value : ExtPointConstants.DEFAULT_VALUE;
    }

    /**
     * 检查上下文是否有效（至少包含租户或业务标识）
     * 
     * @return 是否有效
     */
    public boolean isValid() {
        return StringUtils.hasText(tenantCode) || StringUtils.hasText(bizCode);
    }

    /**
     * 设置扩展属性（线程安全）
     * 
     * @param key 属性键
     * @param value 属性值
     * @return 当前上下文实例，支持链式调用
     */
    public BizContext<T> withAttribute(String key, Object value) {
        Objects.requireNonNull(key, "Attribute key must not be null");
        
        if (attributes == null) {
            attributes = new ConcurrentHashMap<>();
        }
        attributes.put(key, value);
        return this;
    }

    /**
     * 获取扩展属性
     * 
     * @param key 属性键
     * @return 属性值，如果不存在则返回null
     */
    @SuppressWarnings("unchecked")
    public <V> V getAttribute(String key) {
        Objects.requireNonNull(key, "Attribute key must not be null");
        
        if (attributes == null) {
            return null;
        }
        return (V) attributes.get(key);
    }

    /**
     * 获取扩展属性，带默认值
     * 
     * @param key 属性键
     * @param defaultValue 默认值
     * @return 属性值，如果不存在则返回默认值
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
     * 
     * @param key 属性键
     * @param mappingFunction 属性值计算函数
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
     * @param key 属性键
     * @return 是否包含该属性
     */
    public boolean hasAttribute(String key) {
        Objects.requireNonNull(key, "Attribute key must not be null");
        return attributes != null && attributes.containsKey(key);
    }
    
    /**
     * 检查是否包含指定的扩展属性（兼容方法）
     * 
     * @param key 属性键
     * @return 是否包含该属性
     */
    public boolean containsAttribute(String key) {
        return hasAttribute(key);
    }

    /**
     * 移除指定的扩展属性
     * 
     * @param key 属性键
     * @return 被移除的属性值，如果不存在则返回null
     */
    @SuppressWarnings("unchecked")
    public <V> V removeAttribute(String key) {
        Objects.requireNonNull(key, "Attribute key must not be null");
        
        if (attributes == null) {
            return null;
        }
        return (V) attributes.remove(key);
    }

    /**
     * 获取所有扩展属性的副本
     * 
     * @return 扩展属性映射的副本
     */
    public Map<String, Object> getAttributes() {
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
    public BizContext<T> withAttributes(Map<String, Object> attributes) {
        this.attributes = attributes != null ? new ConcurrentHashMap<>(attributes) : null;
        return this;
    }

    /**
     * 合并另一个上下文中的属性到当前上下文
     * 
     * @param other 要合并的上下文
     * @return 当前上下文实例，支持链式调用
     */
    public BizContext<T> merge(BizContext<?> other) {
        if (other == null) {
            return this;
        }
        
        // 合并标准维度（仅当当前值为空时）
        if (!StringUtils.hasText(tenantCode) && StringUtils.hasText(other.getTenantCode())) {
            this.tenantCode = other.getTenantCode();
        }
        if (!StringUtils.hasText(bizCode) && StringUtils.hasText(other.getBizCode())) {
            this.bizCode = other.getBizCode();
        }
        if (!StringUtils.hasText(useCase) && StringUtils.hasText(other.getUseCase())) {
            this.useCase = other.getUseCase();
        }
        if (!StringUtils.hasText(scenario) && StringUtils.hasText(other.getScenario())) {
            this.scenario = other.getScenario();
        }
        
        // 合并扩展属性
        if (other.getAttributes() != null && !other.getAttributes().isEmpty()) {
            if (this.attributes == null) {
                this.attributes = new ConcurrentHashMap<>();
            }
            this.attributes.putAll(other.getAttributes());
        }
        
        return this;
    }

    /**
     * 创建当前上下文的副本
     * 
     * @return 上下文副本
     */
    public BizContext<T> copy() {
        return this.toBuilder()
                .attributes(attributes != null ? new ConcurrentHashMap<>(attributes) : null)
                .build();
    }

    /**
     * 构建器内部类，提供流畅的API
     */
    public static class Builder<T> {
        /**
         * 添加扩展属性
         */
        public Builder<T> attribute(String key, Object value) {
            Objects.requireNonNull(key, "Attribute key must not be null");
            
            if (attributes == null) {
                attributes = new ConcurrentHashMap<>();
            }
            attributes.put(key, value);
            return this;
        }

        /**
         * 添加多个扩展属性
         */
        public Builder<T> attributes(Map<String, Object> attrs) {
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
}