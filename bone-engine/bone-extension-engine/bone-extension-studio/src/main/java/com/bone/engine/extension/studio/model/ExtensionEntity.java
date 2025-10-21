package com.bone.engine.extension.studio.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 扩展实现实体类
 * 用于存储扩展实现的元数据信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
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