package com.bone.engine.extension.studio.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 扩展点实体类
 * 用于存储扩展点的元数据信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
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