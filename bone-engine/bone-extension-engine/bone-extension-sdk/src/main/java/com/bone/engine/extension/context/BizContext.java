package com.bone.engine.extension.context;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.util.CollectionUtils;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 业务上下文对象
 * <p>
 * 用于在扩展点调用过程中传递业务相关的上下文信息，支持泛型设计，可携带特定类型的业务数据
 * <strong>核心功能：</strong>提供路由匹配所需的标识信息，支持上下文属性传递，线程安全的属性管理
 * </p>
 * 
 * <h3>主要功能特性：</h3>
 * <ul>
 *   <li><strong>路由标识管理：</strong>提供租户、业务域、用例、场景等核心路由标识</li>
 *   <li><strong>泛型数据支持：</strong>支持携带特定类型的业务数据对象</li>
 *   <li><strong>动态属性管理：</strong>提供线程安全的属性存储和访问机制</li>
 *   <li><strong>可扩展性：</strong>支持添加自定义属性和元数据</li>
 *   <li><strong>构建者模式：</strong>通过Builder模式提供流畅的API</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>
 * {@code
 * // 创建并配置业务上下文
 * BizContext<Order> context = BizContext.<Order>builder()
 *     .tenantCode("TENANT_A")
 *     .bizCode("ORDER")
 *     .useCase("CREATE")
 *     .scenario("NORMAL")
 *     .env("PROD")
 *     .data(order)
 *     .putAttribute("userId", "123456")
 *     .putAttribute("requestId", "REQ-2023-0001")
 *     .build();
 * 
 * // 在扩展点实现中使用上下文
 * public PaymentResult pay(PaymentRequest request, BizContext<?> context) {
 *     String tenantCode = context.getTenantCode();
 *     String userId = context.getAttribute("userId");
 *     // 业务逻辑处理
 *     return new PaymentResult();
 * }
 * }
 * </pre>
 *
 * @param <T> 业务数据对象类型
 * @author Bone Engine Team
 * @version 1.0.0
 * @see BizContextHolder 上下文持有者工具类
 */
@Getter
@Setter
@ToString
public class BizContext<T> implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 租户代码
     */
    private String tenantCode;
    
    /**
     * 业务域代码
     */
    private String bizCode;
    
    /**
     * 用例代码
     */
    private String useCase;
    
    /**
     * 场景代码
     */
    private String scenario;
    
    /**
     * 环境标识
     */
    private String env;
    
    /**
     * 分组标识
     */
    private String group;
    
    /**
     * 业务数据对象
     */
    private T data;
    
    /**
     * 上下文创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 上下文属性映射表（线程安全）
     */
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    
    /**
     * 上下文元数据
     */
    private final Map<String, String> metadata = new HashMap<>();
    
    /**
     * 构建者构造函数
     */
    @Builder
    public BizContext(String tenantCode, String bizCode, String useCase, String scenario, 
                     String env, String group, T data, Map<String, Object> attributes) {
        this.tenantCode = tenantCode;
        this.bizCode = bizCode;
        this.useCase = useCase;
        this.scenario = scenario;
        this.env = env;
        this.group = group;
        this.data = data;
        this.createTime = LocalDateTime.now();
        
        // 初始化属性映射
        if (!CollectionUtils.isEmpty(attributes)) {
            this.attributes.putAll(attributes);
        }
    }
    
    /**
     * 添加上下文属性
     * 
     * @param key 属性键
     * @param value 属性值
     * @return 当前上下文实例（用于链式调用）
     */
    public BizContext<T> putAttribute(String key, Object value) {
        if (key != null) {
            if (value != null) {
                this.attributes.put(key, value);
            } else {
                this.attributes.remove(key);
            }
        }
        return this;
    }
    
    /**
     * 获取上下文属性
     * 
     * @param key 属性键
     * @return 属性值
     */
    @SuppressWarnings("unchecked")
    public <V> V getAttribute(String key) {
        if (key == null) {
            return null;
        }
        return (V) this.attributes.get(key);
    }
    
    /**
     * 获取上下文属性（带默认值）
     * 
     * @param key 属性键
     * @param defaultValue 默认值
     * @return 属性值或默认值
     */
    @SuppressWarnings("unchecked")
    public <V> V getAttributeOrDefault(String key, V defaultValue) {
        if (key == null) {
            return defaultValue;
        }
        return (V) this.attributes.getOrDefault(key, defaultValue);
    }
    
    /**
     * 移除上下文属性
     * 
     * @param key 属性键
     * @return 当前上下文实例（用于链式调用）
     */
    public BizContext<T> removeAttribute(String key) {
        if (key != null) {
            this.attributes.remove(key);
        }
        return this;
    }
    
    /**
     * 检查是否包含指定属性
     * 
     * @param key 属性键
     * @return 是否包含
     */
    public boolean containsAttribute(String key) {
        return key != null && this.attributes.containsKey(key);
    }
    
    /**
     * 获取所有属性
     * 
     * @return 属性映射表（只读）
     */
    public Map<String, Object> getAllAttributes() {
        return java.util.Collections.unmodifiableMap(this.attributes);
    }
    
    /**
     * 添加元数据
     * 
     * @param key 元数据键
     * @param value 元数据值
     * @return 当前上下文实例（用于链式调用）
     */
    public BizContext<T> putMetadata(String key, String value) {
        if (key != null) {
            if (value != null) {
                this.metadata.put(key, value);
            } else {
                this.metadata.remove(key);
            }
        }
        return this;
    }
    
    /**
     * 获取元数据
     * 
     * @param key 元数据键
     * @return 元数据值
     */
    public String getMetadata(String key) {
        return key != null ? this.metadata.get(key) : null;
    }
    
    /**
     * 获取所有元数据
     * 
     * @return 元数据映射表（只读）
     */
    public Map<String, String> getAllMetadata() {
        return java.util.Collections.unmodifiableMap(this.metadata);
    }
    
    /**
     * 创建空的上下文实例
     * 
     * @return 空上下文实例
     */
    public static <T> BizContext<T> createEmpty() {
        return BizContext.<T>builder().build();
    }
    
    /**
     * 克隆当前上下文（深度拷贝）
     * 
     * @return 克隆后的上下文实例
     */
    @SuppressWarnings("unchecked")
    public BizContext<T> clone() {
        BizContext<T> cloned = BizContext.<T>builder()
            .tenantCode(this.tenantCode)
            .bizCode(this.bizCode)
            .useCase(this.useCase)
            .scenario(this.scenario)
            .env(this.env)
            .group(this.group)
            .data(this.data) // 注意：data对象本身不会被深拷贝
            .build();
        
        // 复制属性和元数据
        cloned.attributes.putAll(this.attributes);
        cloned.metadata.putAll(this.metadata);
        
        return cloned;
    }
}