package com.bone.smartmeta.engine.annotation;

import java.lang.annotation.*;

/**
 * 实体注解，用于通过代码定义实体元数据
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
}
