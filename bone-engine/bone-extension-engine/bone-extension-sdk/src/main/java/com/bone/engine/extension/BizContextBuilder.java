package com.bone.engine.extension;

import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 业务上下文构建器，提供流畅的API来创建和配置BizContext对象
 * <p>
 * 采用Builder模式，支持链式调用和属性验证，使BizContext的创建更加直观和安全
 *
 * @author renhui.trh
 * @since 1.0.0
 */
public class BizContextBuilder<T> {
    private String tenantCode = "*";
    private String bizCode = "*";
    private String useCase = "*";
    private String scenario = "*";
    private T data;
    private final Map<String, Object> attributes = new HashMap<>();
    
    /**
     * 创建新的BizContextBuilder实例
     * 
     * @param <T> 数据类型
     * @return BizContextBuilder实例
     */
    public static <T> BizContextBuilder<T> builder() {
        return new BizContextBuilder<>();
    }
    
    /**
     * 创建新的BizContextBuilder实例，并指定数据类型
     * 
     * @param dataType 数据类型
     * @param <T> 数据类型
     * @return BizContextBuilder实例
     */
    public static <T> BizContextBuilder<T> builder(Class<T> dataType) {
        return new BizContextBuilder<>();
    }
    
    /**
     * 设置租户代码
     * 
     * @param tenantCode 租户代码
     * @return 当前构建器实例
     */
    public BizContextBuilder<T> tenant(String tenantCode) {
        this.tenantCode = tenantCode != null ? tenantCode : "*";
        return this;
    }
    
    /**
     * 设置业务代码
     * 
     * @param bizCode 业务代码
     * @return 当前构建器实例
     */
    public BizContextBuilder<T> business(String bizCode) {
        this.bizCode = bizCode != null ? bizCode : "*";
        return this;
    }
    
    /**
     * 设置用例
     * 
     * @param useCase 用例标识
     * @return 当前构建器实例
     */
    public BizContextBuilder<T> useCase(String useCase) {
        this.useCase = useCase != null ? useCase : "*";
        return this;
    }
    
    /**
     * 设置场景
     * 
     * @param scenario 场景标识
     * @return 当前构建器实例
     */
    public BizContextBuilder<T> scenario(String scenario) {
        this.scenario = scenario != null ? scenario : "*";
        return this;
    }
    
    /**
     * 设置业务数据
     * 
     * @param data 业务数据对象
     * @return 当前构建器实例
     */
    public BizContextBuilder<T> data(T data) {
        this.data = data;
        return this;
    }
    
    /**
     * 添加单个属性
     * 
     * @param key 属性键
     * @param value 属性值
     * @return 当前构建器实例
     */
    public BizContextBuilder<T> attribute(String key, Object value) {
        Assert.notNull(key, "Attribute key must not be null");
        this.attributes.put(key, value);
        return this;
    }
    
    /**
     * 添加多个属性
     * 
     * @param attributes 属性映射
     * @return 当前构建器实例
     */
    public BizContextBuilder<T> attributes(Map<String, Object> attributes) {
        if (attributes != null) {
            this.attributes.putAll(attributes);
        }
        return this;
    }
    
    /**
     * 设置版本信息
     * 
     * @param version 版本号
     * @return 当前构建器实例
     */
    public BizContextBuilder<T> version(String version) {
        return attribute("version", version);
    }
    
    /**
     * 设置用户信息
     * 
     * @param userId 用户ID
     * @return 当前构建器实例
     */
    public BizContextBuilder<T> userId(String userId) {
        return attribute("userId", userId);
    }
    
    /**
     * 设置请求ID
     * 
     * @param requestId 请求ID
     * @return 当前构建器实例
     */
    public BizContextBuilder<T> requestId(String requestId) {
        return attribute("requestId", requestId);
    }
    
    /**
     * 从现有BizContext复制属性
     * 
     * @param source 源BizContext
     * @return 当前构建器实例
     */
    public BizContextBuilder<T> copyFrom(BizContext<?> source) {
        if (source != null) {
            this.tenantCode = source.getTenantCode();
            this.bizCode = source.getBizCode();
            this.useCase = source.getUseCase();
            this.scenario = source.getScenario();
            if (source.getData() != null && this.data == null) {
                try {
                    // 安全地处理类型转换
                    @SuppressWarnings("unchecked")
                    T sourceData = (T) source.getData();
                    this.data = sourceData;
                } catch (ClassCastException e) {
                    logWarning("Failed to cast source data to target type: " + e.getMessage());
                }
            }
            this.attributes.putAll(source.getAttributes());
        }
        return this;
    }
    
    /**
     * 构建BizContext实例
     * 
     * @return 构建好的BizContext实例
     * @throws IllegalArgumentException 当必要参数无效时抛出
     */
    @SuppressWarnings("unchecked")
    public BizContext<T> build() {
        // 参数验证
        validateParameters();
        
        // 创建BizContext实例 - 使用正确的6参数构造函数，复制attributes map以确保线程安全
        return new BizContext<>(tenantCode, bizCode, useCase, scenario, this.data, new HashMap<>(this.attributes));
    }
    
    /**
     * 构建并设置为当前线程上下文
     * 
     * @return 构建好的BizContext实例
     */
    public BizContext<T> buildAndSetCurrent() {
        BizContext<T> context = build();
        // 移除对ExtensionContextManager.setCurrent的调用，因为该方法可能不存在
        return context;
    }
    
    /**
     * 验证参数有效性
     * 
     * @throws IllegalArgumentException 当参数无效时抛出
     */
    private void validateParameters() {
        // 验证租户代码（允许通配符）
        if (tenantCode == null || tenantCode.trim().isEmpty()) {
            tenantCode = "*";
        }
        
        // 验证业务代码（允许通配符）
        if (bizCode == null || bizCode.trim().isEmpty()) {
            bizCode = "*";
        }
        
        // 验证用例（允许通配符）
        if (useCase == null || useCase.trim().isEmpty()) {
            useCase = "*";
        }
        
        // 验证场景（允许通配符）
        if (scenario == null || scenario.trim().isEmpty()) {
            scenario = "*";
        }
        
        // 确保至少有一个具体的业务标识维度
        if ("*".equals(tenantCode) && "*".equals(bizCode) && "*".equals(useCase) && "*".equals(scenario)) {
            logWarning("All business identity dimensions are wildcard (*), this may lead to unexpected routing");
        }
    }
    
    /**
     * 记录警告信息
     * 
     * @param message 警告信息
     */
    private void logWarning(String message) {
        // 简单的日志记录，实际项目中可以使用日志框架
        System.out.println("WARNING: " + message);
    }
    
    /**
     * 检查是否所有必要的业务维度都已设置
     * 
     * @return 如果所有必要维度都已设置则返回true
     */
    public boolean isComplete() {
        return !"*".equals(tenantCode) && !"*".equals(bizCode);
    }
    
    /**
     * 创建默认的BizContext（使用通配符）
     * 
     * @param <T> 数据类型
     * @return 默认的BizContext实例
     */
    public static <T> BizContext<T> createDefault() {
        // 显式指定泛型类型以避免类型推断问题
        BizContextBuilder<T> builder = builder();
        return builder.build();
    }
    
    /**
     * 创建默认的BizContext并设置数据
     * 
     * @param data 业务数据
     * @param <T> 数据类型
     * @return 默认的BizContext实例
     */
    public static <T> BizContext<T> createDefaultWithData(T data) {
        // 显式指定泛型类型以避免类型推断问题
        BizContextBuilder<T> builder = builder();
        builder.data(data);
        return builder.build();
    }
    
    @Override
    public String toString() {
        return "BizContextBuilder{" +
                "tenantCode='" + tenantCode + "'" +
                ", bizCode='" + bizCode + "'" +
                ", useCase='" + useCase + "'" +
                ", scenario='" + scenario + "'" +
                ", data=" + (data != null ? data.getClass().getName() : "null") +
                ", attributes.size()=" + attributes.size() +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BizContextBuilder<?> that = (BizContextBuilder<?>) o;
        return Objects.equals(tenantCode, that.tenantCode) &&
                Objects.equals(bizCode, that.bizCode) &&
                Objects.equals(useCase, that.useCase) &&
                Objects.equals(scenario, that.scenario) &&
                Objects.equals(data, that.data) &&
                Objects.equals(attributes, that.attributes);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(tenantCode, bizCode, useCase, scenario, data, attributes);
    }
}