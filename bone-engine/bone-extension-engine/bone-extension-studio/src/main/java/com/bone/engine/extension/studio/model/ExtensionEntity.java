package com.bone.engine.extension.studio.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 扩展实现实体类
 * 用于存储扩展实现的元数据信息
 */

/**
 * 扩展实现实体类
 * 用于存储扩展实现的元数据信息
 */
@Entity
@Table(name = "bone_extension_implementation")
public class ExtensionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的扩展点
     */
    @ManyToOne
    @JoinColumn(name = "ext_point_id", nullable = false)
    private ExtPointEntity extPoint;

    /**
     * 扩展实现名称
     */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /**
     * 扩展实现描述
     */
    @Column(name = "description", length = 1000)
    private String description;

    /**
     * 实现类全限定名
     */
    @Column(name = "class_name", nullable = false, length = 500)
    private String className;

    /**
     * 租户代码
     */
    @Column(name = "tenant_code", nullable = false, length = 100)
    private String tenantCode = "DEFAULT";

    /**
     * 业务域代码
     */
    @Column(name = "biz_code", nullable = false, length = 100)
    private String bizCode = "*";

    /**
     * 用例代码
     */
    @Column(name = "use_case", length = 100)
    private String useCase = "*";

    /**
     * 场景代码
     */
    @Column(name = "scenario", length = 100)
    private String scenario = "*";

    /**
     * 用户组标识
     */
    @Column(name = "user_group", length = 100)
    private String userGroup = "*";
    
    // 构造函数
    public ExtensionEntity() {
        // 默认构造函数
    }
    
    // Getter和Setter方法
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public ExtPointEntity getExtPoint() {
        return extPoint;
    }
    
    public void setExtPoint(ExtPointEntity extPoint) {
        this.extPoint = extPoint;
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
    
    public String getClassName() {
        return className;
    }
    
    public void setClassName(String className) {
        this.className = className;
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
    
    public String getUserGroup() {
        return userGroup;
    }
    
    public void setUserGroup(String userGroup) {
        this.userGroup = userGroup;
    }

    /**
     * 环境标识
     */
    @Column(name = "env", length = 50)
    private String env = "*";

    /**
     * 优先级
     */
    @Column(name = "priority", nullable = false)
    private int priority = 100;

    /**
     * 条件表达式
     */
    @Column(name = "condition", length = 1000)
    private String condition;

    /**
     * 标签配置（JSON格式）
     */
    @Column(name = "tags", length = 2000)
    private String tags;

    /**
     * 扩展实现版本
     */
    @Column(name = "version", length = 50, nullable = false)
    private String version = "1.0.0";

    /**
     * 是否启用
     */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
    
    public String getEnv() {
        return env;
    }
    
    public void setEnv(String env) {
        this.env = env;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public void setPriority(int priority) {
        this.priority = priority;
    }
    
    public String getCondition() {
        return condition;
    }
    
    public void setCondition(String condition) {
        this.condition = condition;
    }
    
    public String getTags() {
        return tags;
    }
    
    public void setTags(String tags) {
        this.tags = tags;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 创建时间
     */
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    /**
     * 额外配置（JSON格式）
     */
    @Column(name = "config", length = 4000)
    private String config;

    /**
     * 调用统计信息（JSON格式）
     */
    @Column(name = "statistics", length = 2000)
    private String statistics;
    
    public LocalDateTime getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
    
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }
    
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
    
    public String getConfig() {
        return config;
    }
    
    public void setConfig(String config) {
        this.config = config;
    }
    
    public String getStatistics() {
        return statistics;
    }
    
    public void setStatistics(String statistics) {
        this.statistics = statistics;
    }

    /**
     * JPA回调，创建前设置时间戳
     */
    @PrePersist
    protected void onCreate() {
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    /**
     * JPA回调，更新前设置更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        this.updateTime = LocalDateTime.now();
    }
}