package com.bone.engine.extension.metadata;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 扩展实现元数据
 * <p>
 * 存储扩展实现类的元数据信息
 * </p>
 * 
 * @since 1.0.0
 */
public class ExtImplMetadata {
    
    private static final Logger log = Logger.getLogger(ExtImplMetadata.class.getName());
    
    // 基本信息
    private String className;
    private String name;
    private String description;
    private String author;
    private String version;
    private String since;
    private boolean deprecated;
    private String deprecatedSince;
    private String expiredSince;
    
    // 路由信息
    private String tenantCode;
    private String bizCode;
    private String useCase;
    private String scenario;
    
    // 元数据属性
    private int priority;
    private boolean defaultImpl;
    private boolean recommended;
    
    // 依赖信息
    private String[] dependsOn;
    
    // 配置属性
    private String[] configProperties;
    
    // 路由条件
    private Map<String, String> routingConditions;
    
    public ExtImplMetadata() {
        this.priority = 0;
        this.routingConditions = new HashMap<>();
    }
    
    public String getClassName() {
        return className;
    }
    
    public void setClassName(String className) {
        this.className = className;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
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
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getSince() {
        return since;
    }
    
    public void setSince(String since) {
        this.since = since;
    }
    
    public boolean isDeprecated() {
        return deprecated;
    }
    
    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }
    
    public String getDeprecatedSince() {
        return deprecatedSince;
    }
    
    public void setDeprecatedSince(String deprecatedSince) {
        this.deprecatedSince = deprecatedSince;
    }
    
    public String getExpiredSince() {
        return expiredSince;
    }
    
    public void setExpiredSince(String expiredSince) {
        this.expiredSince = expiredSince;
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
    
    public int getPriority() {
        return priority;
    }
    
    public void setPriority(int priority) {
        this.priority = priority;
    }
    
    public boolean isDefaultImpl() {
        return defaultImpl;
    }
    
    public void setDefaultImpl(boolean defaultImpl) {
        this.defaultImpl = defaultImpl;
    }
    
    public boolean isRecommended() {
        return recommended;
    }
    
    public void setRecommended(boolean recommended) {
        this.recommended = recommended;
    }
    
    public String[] getDependsOn() {
        return dependsOn;
    }
    
    public void setDependsOn(String[] dependsOn) {
        this.dependsOn = dependsOn;
    }
    
    public String[] getConfigProperties() {
        return configProperties;
    }
    
    public void setConfigProperties(String[] configProperties) {
        this.configProperties = configProperties;
    }
    
    public Map<String, String> getRoutingConditions() {
        return routingConditions;
    }
    
    public void setRoutingConditions(Map<String, String> routingConditions) {
        this.routingConditions = routingConditions;
    }
    
    public void addRoutingCondition(String key, String value) {
        this.routingConditions.put(key, value);
    }
    
    @Override
    public String toString() {
        return "ExtImplMetadata{" +
                "className='" + className + '\'' +
                ", name='" + name + '\'' +
                ", priority=" + priority +
                ", isDefault=" + defaultImpl +
                ", recommended=" + recommended +
                '}';
    }
}