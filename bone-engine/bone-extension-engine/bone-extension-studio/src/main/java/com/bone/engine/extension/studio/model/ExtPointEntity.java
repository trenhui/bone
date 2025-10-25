package com.bone.engine.extension.studio.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 扩展点实体类
 * 用于存储扩展点的元数据信息
 */
@Entity
@Table(name = "bone_extension_point")
public class ExtPointEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 扩展点名称
     */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /**
     * 扩展点描述
     */
    @Column(name = "description", length = 1000)
    private String description;

    /**
     * 接口全限定名
     */
    @Column(name = "interface_name", nullable = false, length = 500, unique = true)
    private String interfaceName;

    /**
     * 领域分类
     */
    @Column(name = "domain", length = 100)
    private String domain;

    /**
     * 功能分类
     */
    @Column(name = "category", length = 100)
    private String category;

    /**
     * 扩展点类型
     */
    @Column(name = "type", length = 50, nullable = false)
    private String type = "BUSINESS";

    /**
     * 版本号
     */
    @Column(name = "version", length = 50, nullable = false)
    private String version = "1.0.0";

    /**
     * 是否启用
     */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /**
     * 是否废弃
     */
    @Column(name = "deprecated", nullable = false)
    private boolean deprecated = false;

    /**
     * 废弃版本
     */
    @Column(name = "deprecated_since", length = 50)
    private String deprecatedSince;

    /**
     * 废弃于版本
     */
    @Column(name = "deprecated_in", length = 50)
    private String deprecatedIn;

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
     * 关联的扩展实现列表
     */
    @OneToMany(mappedBy = "extPoint", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExtensionEntity> extensions = new ArrayList<>();
    
    // 构造函数
    public ExtPointEntity() {
        // 默认构造函数
    }
    
    // Getter和Setter方法
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
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
    
    public String getInterfaceName() {
        return interfaceName;
    }
    
    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }
    
    public String getDomain() {
        return domain;
    }
    
    public void setDomain(String domain) {
        this.domain = domain;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
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
    
    public String getDeprecatedIn() {
        return deprecatedIn;
    }
    
    public void setDeprecatedIn(String deprecatedIn) {
        this.deprecatedIn = deprecatedIn;
    }
    
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
    
    public List<ExtensionEntity> getExtensions() {
        return extensions;
    }
    
    public void setExtensions(List<ExtensionEntity> extensions) {
        this.extensions = extensions;
    }
    
    // 添加单个扩展实现
    public void addExtension(ExtensionEntity extension) {
        if (extension != null) {
            extensions.add(extension);
            extension.setExtPoint(this);
        }
    }
    
    // 移除单个扩展实现
    public void removeExtension(ExtensionEntity extension) {
        if (extension != null) {
            extensions.remove(extension);
            extension.setExtPoint(null);
        }
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