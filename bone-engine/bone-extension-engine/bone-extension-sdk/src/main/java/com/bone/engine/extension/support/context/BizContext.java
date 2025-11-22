package com.bone.engine.extension.support.context;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
 *   <li><strong>标签系统：</strong>支持路由和监控的标签机制</li>
 *   <li><strong>构建者模式：</strong>通过Builder模式提供流畅的API</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>
 * {@code
 * // 创建并配置业务上下文
 * BizContext<Order> context = BizContext.<Order>builder()
 *     .tenant("TENANT_A")
 *     .bizCode("ORDER")
 *     .useCase("CREATE")
 *     .scenario("NORMAL")
 *     .env("PROD")
 *     .userGroup("GOLD")
 *     .data(order)
 *     .putAttribute("userId", "123456")
 *     .addTag("channel", "APP")
 *     .addTag("activity", "NEW_YEAR")
 *     .build();
 * 
 * // 在扩展点实现中使用上下文
 * public PaymentResult pay(PaymentRequest request, BizContext<?> context) {
 *     String tenant = context.getTenant();
 *     String userGroup = context.getUserGroup();
 *     String userId = context.getAttribute("userId");
 *     // 业务逻辑处理
 *     return new PaymentResult();
 * }
 * 
 * // 简化创建方式
 * BizContext<Order> simpleContext = BizContext.of("ORDER", "CREATE")
 *     .data(order)
 *     .build();
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
@ToString(exclude = {"data", "attributes", "metadata", "tags", "headers"}) // 排除可能包含敏感或大型数据的字段
public class BizContext<T> implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 租户代码
     */
    private String tenant = "DEFAULT";
    
    /**
     * 获取租户代码
     * @return 租户代码
     */
    public String getTenant() {
        return tenant;
    }

    
    /**
     * 业务域代码
     */
    private String bizCode;
    
    /**
     * 获取业务域代码
     * @return 业务域代码
     */
    public String getBizCode() {
        return bizCode;
    }
    
    /**
     * 获取业务域（别名方法）
     * @return 业务域代码
     */
    public String getBusinessDomain() {
        return getBizCode();
    }
    
    /**
     * 用例代码
     */
    private String useCase;
    
    /**
     * 获取用例代码
     * @return 用例代码
     */
    public String getUseCase() {
        return useCase;
    }
    
    /**
     * 场景代码
     */
    private String scenario;
    
    /**
     * 获取场景代码
     * @return 场景代码
     */
    public String getScenario() {
        return scenario;
    }
    
    /**
     * 环境标识
     */
    private String env = "PROD";
    
    /**
     * 分组标识
     */
    private String group;
    
    /**
     * 用户组标识
     */
    private String userGroup = "DEFAULT";
    
    /**
     * 请求ID
     */
    private String requestId;
    
    /**
     * 请求头信息（线程安全）
     */
    private final Map<String, String> headers = new ConcurrentHashMap<>();
    
    /**
     * 业务数据对象
     */
    private T data;
    
    /**
     * 上下文创建时间
     */
    private final LocalDateTime createTime;
    
    /**
     * 上下文属性映射表（线程安全）
     */
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    
    /**
     * 上下文元数据（线程安全）
     */
    private final Map<String, String> metadata = new ConcurrentHashMap<>();
    
    /**
     * 标签信息，用于路由和监控（线程安全）
     */
    private final Map<String, Object> tags = new ConcurrentHashMap<>();
    
    /**
     * 构建者构造函数
     */
    @Builder
    public BizContext(String tenant, String bizCode, String useCase, String scenario,
                      String env, String group, String userGroup, String requestId,
                      T data, Map<String, Object> attributes, Map<String, String> headers) {
        this.tenant = tenant;
        this.bizCode = bizCode;
        this.useCase = useCase;
        this.scenario = scenario;
        this.env = env;
        this.group = group;
        this.userGroup = userGroup;
        this.requestId = StringUtils.hasText(requestId) ? requestId : generateRequestId();
        this.data = data;
        this.createTime = LocalDateTime.now();
        
        // 初始化属性映射
        if (!CollectionUtils.isEmpty(attributes)) {
            this.attributes.putAll(attributes);
        }
        
        // 初始化请求头
        if (!CollectionUtils.isEmpty(headers)) {
            this.headers.putAll(headers);
        }
    }
    
    /**
     * 生成请求ID
     */
    private String generateRequestId() {
        return "EXT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
    
    // 移除手动实现的builder方法，让lombok自动生成
    
    /**
     * 简化创建方式
     */
    public static <T> BizContext<T> of(String bizCode, String scenario) {
        return new BizContext<>(null, bizCode, null, scenario, null, null, null, null, null, null, null);
    }
    
    /**
     * 带租户的简化创建方式
     */
    public static <T> BizContext<T> ofTenant(String tenantCode, String bizCode, String scenario) {
        return new BizContext<>(tenantCode, bizCode, null, scenario, null, null, null, null, null, null, null);
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
        V value = (V) this.attributes.get(key);
        return value != null ? value : defaultValue;
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
        return Collections.unmodifiableMap(this.attributes);
    }
    
    /**
     * 添加元数据
     * 
     * @param key 元数据键
     * @param value 元数据值
     * @return 当前上下文实例（用于链式调用）
     */
    public BizContext<T> putMetadata(String key, String value) {
        if (StringUtils.hasText(key)) {
            if (StringUtils.hasText(value)) {
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
        return Collections.unmodifiableMap(this.metadata);
    }
    
    /**
     * 添加标签（用于路由和监控）
     * 
     * @param key 标签键
     * @param value 标签值
     * @return 当前上下文实例（用于链式调用）
     */
    public BizContext<T> addTag(String key, Object value) {
        if (StringUtils.hasText(key)) {
            if (value != null) {
                this.tags.put(key, value);
            } else {
                this.tags.remove(key);
            }
        }
        return this;
    }
    
    /**
     * 获取标签值
     * 
     * @param key 标签键
     * @return 标签值
     */
    @SuppressWarnings("unchecked")
    public <V> V getTag(String key) {
        return key != null ? (V) this.tags.get(key) : null;
    }
    
    /**
     * 获取所有标签
     * 
     * @return 标签映射表（只读）
     */
    public Map<String, Object> getAllTags() {
        return Collections.unmodifiableMap(this.tags);
    }
    
    /**
     * 添加请求头
     * 
     * @param key 头信息键
     * @param value 头信息值
     * @return 当前上下文实例（用于链式调用）
     */
    public BizContext<T> addHeader(String key, String value) {
        if (StringUtils.hasText(key)) {
            if (StringUtils.hasText(value)) {
                this.headers.put(key, value);
            } else {
                this.headers.remove(key);
            }
        }
        return this;
    }
    
    /**
     * 获取请求头
     * 
     * @param key 头信息键
     * @return 头信息值
     */
    public String getHeader(String key) {
        return key != null ? this.headers.get(key) : null;
    }
    
    /**
     * 获取所有请求头
     * 
     * @return 请求头映射表（只读）
     */
    public Map<String, String> getAllHeaders() {
        return Collections.unmodifiableMap(this.headers);
    }
    
    /**
     * 创建空的上下文实例
     * 
     * @return 空上下文实例
     */
    public static <T> BizContext<T> createEmpty() {
        return new BizContext<>(null, null, null, null, null, null, null, null, null, null, null);
    }
    
    /**
     * 克隆当前上下文（深度拷贝）
     * 
     * @return 克隆后的上下文实例
     */
    @SuppressWarnings("unchecked")
    public BizContext<T> clone() {
        BizContext<T> cloned = new BizContext<>(
            this.tenant,
            this.bizCode,
            this.useCase,
            this.scenario,
            this.env,
            this.group,
            this.userGroup,
            this.requestId + "_CLONE", // 添加标记以区分克隆的上下文
            this.data, // 注意：data对象本身不会被深拷贝
            new HashMap<>(this.attributes),
            new HashMap<>(this.headers)
        );
        
        // 复制属性和元数据 - 使用putAll保证线程安全
        cloned.attributes.putAll(this.attributes);
        cloned.metadata.putAll(this.metadata);
        cloned.tags.putAll(this.tags);
        cloned.headers.putAll(this.headers);
        
        return cloned;
    }
    
    /**
     * 比较两个上下文是否相等
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BizContext<?> that = (BizContext<?>) o;
        return Objects.equals(requestId, that.requestId);
    }
    
    /**
     * 获取哈希码
     */
    @Override
    public int hashCode() {
        return Objects.hash(requestId);
    }
    
    /**
     * 合并另一个上下文的信息到当前上下文
     * 
     * @param other 另一个上下文
     * @return 当前上下文实例（用于链式调用）
     */
    public BizContext<T> merge(BizContext<?> other) {
        if (other == null) {
            return this;
        }
        
        // 合并基础信息（仅当当前值为空时）
        if (!StringUtils.hasText(this.tenant) && StringUtils.hasText(other.getTenant())) {
            this.tenant = other.getTenant();
        }
        if (!StringUtils.hasText(this.bizCode) && StringUtils.hasText(other.getBizCode())) {
            this.bizCode = other.getBizCode();
        }
        if (!StringUtils.hasText(this.useCase) && StringUtils.hasText(other.getUseCase())) {
            this.useCase = other.getUseCase();
        }
        if (!StringUtils.hasText(this.scenario) && StringUtils.hasText(other.getScenario())) {
            this.scenario = other.getScenario();
        }
        
        // 合并集合信息（不覆盖现有值）
        other.getAllAttributes().forEach((key, value) -> {
            if (!this.attributes.containsKey(key)) {
                this.attributes.put(key, value);
            }
        });
        
        other.getAllMetadata().forEach((key, value) -> {
            if (!this.metadata.containsKey(key)) {
                this.metadata.put(key, value);
            }
        });
        
        other.getAllTags().forEach((key, value) -> {
            if (!this.tags.containsKey(key)) {
                this.tags.put(key, value);
            }
        });
        
        other.getAllHeaders().forEach((key, value) -> {
            if (!this.headers.containsKey(key)) {
                this.headers.put(key, value);
            }
        });
        
        return this;
    }
}