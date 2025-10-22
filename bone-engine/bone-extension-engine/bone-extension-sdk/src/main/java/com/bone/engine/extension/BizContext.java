package com.bone.engine.extension;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
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
 */
@Builder
@Accessors(chain = true)
public class BizContext<T> {
    // 标准业务维度
    @Nullable
    private final String tenantCode;  // 租户编码
    @Nullable
    private final String bizCode;     // 业务编码
    @Nullable
    private final String useCase;     // 用例编码
    @Nullable
    private final String scenario;    // 场景编码
    @Nullable
    private final String env;         // 环境标识
    @Nullable
    private final String group;       // 分组标识
    @Nullable
    private final T data;             // 业务数据对象
    
    // 扩展属性，使用ConcurrentHashMap保证线程安全
    private final Map<String, Object> attributes;
    
    // 手动添加getter方法
    public String getTenantCode() {
        return tenantCode;
    }
    
    public String getBizCode() {
        return bizCode;
    }
    
    public String getUseCase() {
        return useCase;
    }
    
    public String getScenario() {
        return scenario;
    }
    
    public String getEnv() {
        return env;
    }
    
    public String getGroup() {
        return group;
    }
    
    public T getData() {
        return data;
    }
    
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * 创建空的业务上下文实例
     */
    public static <T> BizContext<T> create() {
        return BizContext.<T>builder().build();
    }

    /**
     * 使用租户编码创建业务上下文
     */
    public static <T> BizContext<T> ofTenant(String tenantCode) {
        return BizContext.<T>builder().tenantCode(tenantCode).build();
    }

    /**
     * 使用业务编码创建业务上下文
     */
    public static <T> BizContext<T> ofBusiness(String bizCode) {
        return BizContext.<T>builder().bizCode(bizCode).build();
    }

    /**
     * 使用租户和业务编码创建业务上下文
     */
    public static <T> BizContext<T> of(String tenantCode, String bizCode) {
        return BizContext.<T>builder().tenantCode(tenantCode).bizCode(bizCode).build();
    }
    
    /**
     * 创建包含指定场景编码的新上下文
     */
    public BizContext<T> withScenario(String scenario) {
        return new BizContext<>(tenantCode, bizCode, useCase, scenario, env, group, data, new ConcurrentHashMap<>(attributes));
    }
    
    /**
     * 创建包含指定环境标识的新上下文
     */
    public BizContext<T> withEnv(String env) {
        return new BizContext<>(tenantCode, bizCode, useCase, scenario, env, group, data, new ConcurrentHashMap<>(attributes));
    }
    
    /**
     * 创建包含指定分组标识的新上下文
     */
    public BizContext<T> withGroup(String group) {
        return new BizContext<>(tenantCode, bizCode, useCase, scenario, env, group, data, new ConcurrentHashMap<>(attributes));
    }

    /**
     * 生成业务标识字符串，用于扩展点路由匹配
     */
    public String getBusinessIdentity() {
        StringBuilder sb = new StringBuilder();
        sb.append(StringUtils.hasText(tenantCode) ? tenantCode : "*");
        sb.append("/");
        sb.append(StringUtils.hasText(bizCode) ? bizCode : "*");
        sb.append("/");
        sb.append(StringUtils.hasText(useCase) ? useCase : "*");
        sb.append("/");
        sb.append(StringUtils.hasText(scenario) ? scenario : "*");
        sb.append("/");
        sb.append(StringUtils.hasText(env) ? env : "*");
        sb.append("/");
        sb.append(StringUtils.hasText(group) ? group : "*");
        return sb.toString();
    }

    /**
     * 生成默认业务标识字符串（所有维度使用默认值）
     */
    public String getDefaultBusinessIdentity() {
        return "*/*/*/*/*/*";
    }

    /**
     * 检查上下文是否有效（至少包含租户或业务标识）
     */
    public boolean isValid() {
        return StringUtils.hasText(tenantCode) || StringUtils.hasText(bizCode);
    }

    /**
     * 添加扩展属性（线程安全）
     */
    public BizContext<T> withAttribute(String key, @Nullable Object value) {
        Objects.requireNonNull(key, "Attribute key must not be null");
        attributes.put(key, value);
        return this;
    }

    /**
     * 批量添加扩展属性（线程安全）
     */
    public BizContext<T> withAttributes(@Nullable Map<String, Object> attributes) {
        Objects.requireNonNull(attributes, "Attributes map must not be null");
        this.attributes.putAll(attributes);
        return this;
    }

    /**
     * 获取扩展属性值
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <V> V getAttribute(String key) {
        Objects.requireNonNull(key, "Attribute key must not be null");
        return (V) attributes.get(key);
    }

    /**
     * 获取扩展属性值，如果不存在则返回默认值
     */
    @SuppressWarnings("unchecked")
    public <V> V getAttributeOrDefault(String key, V defaultValue) {
        Objects.requireNonNull(key, "Attribute key must not be null");
        return (V) attributes.getOrDefault(key, defaultValue);
    }

    /**
     * 获取扩展属性值，如果不存在则使用提供的函数计算并存储
     */
    @SuppressWarnings("unchecked")
    public <V> V computeAttributeIfAbsent(String key, Function<String, V> mappingFunction) {
        Objects.requireNonNull(key, "Attribute key must not be null");
        Objects.requireNonNull(mappingFunction, "Mapping function must not be null");
        return (V) attributes.computeIfAbsent(key, mappingFunction);
    }

    /**
     * 检查是否包含指定的属性
     */
    public boolean containsAttribute(String key) {
        Objects.requireNonNull(key, "Attribute key must not be null");
        return attributes.containsKey(key);
    }

    /**
     * 检查是否包含指定的扩展属性（别名方法）
     */
    public boolean hasAttribute(String key) {
        return containsAttribute(key);
    }

    /**
     * 移除指定的属性
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <V> V removeAttribute(String key) {
        Objects.requireNonNull(key, "Attribute key must not be null");
        return (V) attributes.remove(key);
    }

    /**
     * 获取不可修改的属性映射
     */
    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    /**
     * 获取所有扩展属性的副本
     */
    public Map<String, Object> getAllAttributes() {
        return new ConcurrentHashMap<>(attributes);
    }

    /**
     * 创建当前上下文的副本
     */
    public BizContext<T> copy() {
        Map<String, Object> attributesCopy = new ConcurrentHashMap<>(attributes);
        return new BizContext<>(this.tenantCode, this.bizCode, this.useCase, this.scenario, this.env, this.group, this.data, attributesCopy);
    }

    /**
     * 合并另一个上下文中的属性到当前上下文
     */
    public BizContext<T> merge(@Nullable BizContext<?> other) {
        if (other == null) {
            return this;
        }
        
        // 创建新实例以避免修改原始上下文
        BizContext<T> merged = copy();
        
        // 合并标准维度（仅当当前值为空时）
        if (!StringUtils.hasText(merged.tenantCode) && StringUtils.hasText(other.tenantCode)) {
            merged = merged.withTenantCode(other.tenantCode);
        }
        if (!StringUtils.hasText(merged.bizCode) && StringUtils.hasText(other.bizCode)) {
            merged = merged.withBizCode(other.bizCode);
        }
        if (!StringUtils.hasText(merged.useCase) && StringUtils.hasText(other.useCase)) {
            merged = merged.withUseCase(other.useCase);
        }
        if (!StringUtils.hasText(merged.scenario) && StringUtils.hasText(other.scenario)) {
            merged = merged.withScenario(other.scenario);
        }
        if (!StringUtils.hasText(merged.env) && StringUtils.hasText(other.env)) {
            merged = merged.withEnv(other.env);
        }
        if (!StringUtils.hasText(merged.group) && StringUtils.hasText(other.group)) {
            merged = merged.withGroup(other.group);
        }
        
        // 合并扩展属性
        merged.attributes.putAll(other.getAttributes());
        
        return merged;
    }

    // 辅助方法：创建包含指定租户编码的新上下文
    private BizContext<T> withTenantCode(String tenantCode) {
        return new BizContext<>(tenantCode, this.bizCode, this.useCase, this.scenario, 
                this.env, this.group, this.data, new ConcurrentHashMap<>(this.attributes));
    }
    
    // 辅助方法：创建包含指定业务编码的新上下文
    private BizContext<T> withBizCode(String bizCode) {
        return new BizContext<>(this.tenantCode, bizCode, this.useCase, this.scenario, 
                this.env, this.group, this.data, new ConcurrentHashMap<>(this.attributes));
    }
    
    // 辅助方法：创建包含指定用例编码的新上下文
    private BizContext<T> withUseCase(String useCase) {
        return new BizContext<>(this.tenantCode, this.bizCode, useCase, this.scenario, 
                this.env, this.group, this.data, new ConcurrentHashMap<>(this.attributes));
    }
    
    

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BizContext<?> that = (BizContext<?>) o;

        if (!Objects.equals(tenantCode, that.tenantCode)) return false;
        if (!Objects.equals(bizCode, that.bizCode)) return false;
        if (!Objects.equals(useCase, that.useCase)) return false;
        if (!Objects.equals(scenario, that.scenario)) return false;
        if (!Objects.equals(env, that.env)) return false;
        if (!Objects.equals(group, that.group)) return false;
        if (!Objects.equals(data, that.data)) return false;
        return Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantCode, bizCode, useCase, scenario, env, group, data, attributes);
    }

    @Override
    public String toString() {
        return "BizContext{" +
               "tenantCode='" + tenantCode + '\'' +
               ", bizCode='" + bizCode + '\'' +
               ", useCase='" + useCase + '\'' +
               ", scenario='" + scenario + '\'' +
               ", env='" + env + '\'' +
               ", group='" + group + '\'' +
               ", data=" + data +
               ", attributes=" + attributes +
               '}';
    }
    
    // 移除重复的getBizIdentity方法，使用getBusinessIdentity替代
    
    // 私有构造函数，支持全部字段
    private BizContext(String tenantCode, String bizCode, String useCase, String scenario, String env, String group, T data, Map<String, Object> attributes) {
        this.tenantCode = tenantCode;
        this.bizCode = bizCode;
        this.useCase = useCase;
        this.scenario = scenario;
        this.env = env;
        this.group = group;
        this.data = data;
        this.attributes = attributes != null ? attributes : new ConcurrentHashMap<>();
    }
    
    // 内部Builder类，确保线程安全和正确的初始化
    public static class BizContextBuilder<T> {
        private String tenantCode;
        private String bizCode;
        private String useCase;
        private String scenario;
        private String env;
        private String group;
        private T data;
        private Map<String, Object> attributes;
        
        public BizContextBuilder<T> tenantCode(String tenantCode) {
            this.tenantCode = tenantCode;
            return this;
        }
        
        public BizContextBuilder<T> bizCode(String bizCode) {
            this.bizCode = bizCode;
            return this;
        }
        
        public BizContextBuilder<T> useCase(String useCase) {
            this.useCase = useCase;
            return this;
        }
        
        public BizContextBuilder<T> scenario(String scenario) {
            this.scenario = scenario;
            return this;
        }
        
        public BizContextBuilder<T> env(String env) {
            this.env = env;
            return this;
        }
        
        public BizContextBuilder<T> group(String group) {
            this.group = group;
            return this;
        }
        
        public BizContextBuilder<T> data(T data) {
            this.data = data;
            return this;
        }
        
        public BizContextBuilder<T> attribute(String key, Object value) {
            Objects.requireNonNull(key, "Attribute key must not be null");
            if (this.attributes == null) {
                this.attributes = new HashMap<>();
            }
            this.attributes.put(key, value);
            return this;
        }
        
        public BizContextBuilder<T> attributes(Map<String, Object> attributes) {
            this.attributes = attributes;
            return this;
        }
        
        public BizContext<T> build() {
            Map<String, Object> attrs = attributes != null ? new ConcurrentHashMap<>(attributes) : new ConcurrentHashMap<>();
            return new BizContext<>(tenantCode, bizCode, useCase, scenario, env, group, data, attrs);
        }
    }
}