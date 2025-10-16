package com.bone.smartmeta.engine.annotation;

import java.lang.annotation.*;

/**
 * 智能实体注解，用于通过代码定义实体元数据
 * 支持AI增强、动态建模和智能查询优化
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface SmartEntity {

    /**
     * 实体的API名称（唯一标识符）
     */
    String apiName();

    /**
     * 实体的显示标签
     */
    String label();

    /**
     * 实体的复数显示标签
     */
    String pluralLabel() default "";

    /**
     * 数据库表名
     */
    String table() default "";

    /**
     * 业务域
     */
    String domain() default "General";

    /**
     * 实体类别
     */
    String category() default "Business";

    /**
     * 实体描述
     */
    String description() default "";

    /**
     * 所有权模型
     */
    String ownershipModel() default "Private";

    /**
     * 是否启用历史跟踪
     */
    boolean trackHistory() default false;

    /**
     * 查询缓存超时时间（秒）
     */
    int queryCacheTtl() default 3600;

    /**
     * 是否可缓存
     */
    boolean cacheable() default true;

    /**
     * AI增强配置
     * 指定AI模型类型，如GPT-4、Claude等
     */
    String aiModel() default "";

    /**
     * 是否启用AI查询优化
     */
    boolean aiQueryOptimization() default false;

    /**
     * 是否启用AI智能字段填充
     */
    boolean aiFieldAutoFill() default false;

    /**
     * 是否启用AI智能分析
     */
    boolean aiIntelligentAnalysis() default false;

    /**
     * 实体的关键程度，影响缓存策略和性能优化
     */
    EntityImportance importance() default EntityImportance.MEDIUM;

    /**
     * 实体重要性枚举
     */
    enum EntityImportance {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    /**
     * 是否支持动态字段
     */
    boolean dynamicFieldsSupport() default true;

    /**
     * 是否启用热重载
     */
    boolean hotReloadEnabled() default false;
}
