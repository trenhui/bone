package com.bone.engine.extension.metadata;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 扩展点实现元数据模型
 * <p>
 * 用于存储扩展点实现类的完整元数据信息
 * </p>
 * 
 * @since 1.0.0
 */
public class ExtensionImplMetadata implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // 实现类基本信息
    private String implClassName;           // 实现类全限定名
    private String implSimpleName;          // 实现类简单名称
    private String description;             // 实现描述
    private String author;                  // 作者
    private String createTime;              // 创建时间
    private String lastUpdateTime;          // 最后更新时间
    
    // 路由配置信息
    private String tenantCode;              // 租户编码
    private String bizCode;                 // 业务编码
    private String useCase;                 // 用例编码
    private String scenario;                // 场景编码
    private String expression;              // 动态匹配表达式
    
    // 版本信息
    private String version;                 // 版本号
    private String[] compatibleWith;        // 兼容版本列表
    private boolean isDefault;              // 是否默认实现
    private boolean isRecommended;          // 是否推荐实现
    
    // 执行信息
    private String priority;                // 执行优先级
    private String[] dependencies;          // 依赖的其他扩展实现
    private Map<String, String> properties; // 实现配置属性
    
    // 调用统计
    private long invokeCount;               // 调用次数
    private long lastInvokeTime;            // 最后调用时间
    private double avgInvokeTime;           // 平均调用时间(ms)
    
    // 构造函数和getter/setter方法
    public ExtensionImplMetadata() {
    }
    
    public String getImplClassName() {
        return implClassName;
    }
    
    public void setImplClassName(String implClassName) {
        this.implClassName = implClassName;
    }
    
    public String getImplSimpleName() {
        return implSimpleName;
    }
    
    public void setImplSimpleName(String implSimpleName) {
        this.implSimpleName = implSimpleName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getAuthor() {
        return author;
    }
    
    public void setAuthor(String author) {
        this.author = author;
    }
    
    public String getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }
    
    public String getLastUpdateTime() {
        return lastUpdateTime;
    }
    
    public void setLastUpdateTime(String lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }
    
    public String getTenantCode() {
        return tenantCode;
    }
    
    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }
    
    public String getBizCode() {
        return bizCode;
    }
    
    public void setBizCode(String bizCode) {
        this.bizCode = bizCode;
    }
    
    public String getUseCase() {
        return useCase;
    }
    
    public void setUseCase(String useCase) {
        this.useCase = useCase;
    }
    
    public String getScenario() {
        return scenario;
    }
    
    public void setScenario(String scenario) {
        this.scenario = scenario;
    }
    
    public String getExpression() {
        return expression;
    }
    
    public void setExpression(String expression) {
        this.expression = expression;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String[] getCompatibleWith() {
        return compatibleWith;
    }
    
    public void setCompatibleWith(String[] compatibleWith) {
        this.compatibleWith = compatibleWith;
    }
    
    public boolean isDefault() {
        return isDefault;
    }
    
    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }
    
    public boolean isRecommended() {
        return isRecommended;
    }
    
    public void setRecommended(boolean isRecommended) {
        this.isRecommended = isRecommended;
    }
    
    public String getPriority() {
        return priority;
    }
    
    public void setPriority(String priority) {
        this.priority = priority;
    }
    
    public String[] getDependencies() {
        return dependencies;
    }
    
    public void setDependencies(String[] dependencies) {
        this.dependencies = dependencies;
    }
    
    public Map<String, String> getProperties() {
        return properties;
    }
    
    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }
    
    public long getInvokeCount() {
        return invokeCount;
    }
    
    public void setInvokeCount(long invokeCount) {
        this.invokeCount = invokeCount;
    }
    
    public long getLastInvokeTime() {
        return lastInvokeTime;
    }
    
    public void setLastInvokeTime(long lastInvokeTime) {
        this.lastInvokeTime = lastInvokeTime;
    }
    
    public double getAvgInvokeTime() {
        return avgInvokeTime;
    }
    
    public void setAvgInvokeTime(double avgInvokeTime) {
        this.avgInvokeTime = avgInvokeTime;
    }
}