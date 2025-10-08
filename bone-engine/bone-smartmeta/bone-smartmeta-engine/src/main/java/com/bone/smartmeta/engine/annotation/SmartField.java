package com.bone.smartmeta.engine.annotation;

import java.lang.annotation.*;

/**
 * 字段注解，用于通过代码定义字段元数据
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SmartField {

    /**
     * 字段的API名称
     */
    String name();

    /**
     * 字段的显示标签
     */
    String label();

    /**
     * 字段类型
     */
    FieldType type();

    /**
     * 是否必填
     */
    boolean required() default false;

    /**
     * 是否唯一
     */
    boolean unique() default false;

    /**
     * 字段长度
     */
    int length() default 255;

    /**
     * 数字精度（整数部分位数）
     */
    int precision() default 0;

    /**
     * 小数位数
     */
    int scale() default 0;

    /**
     * 默认值
     */
    String defaultValue() default "";

    /**
     * 格式模式
     */
    String pattern() default "";

    /**
     * 引用的实体（适用于Lookup类型）
     */
    String referenceTo() default "";

    /**
     * 选择列表值（适用于Picklist类型）
     */
    String[] picklistValues() default {};

    /**
     * 是否建立索引
     */
    boolean indexed() default false;

    /**
     * 字段描述
     */
    String description() default "";

    /**
     * 是否加密
     */
    boolean encrypted() default false;

    /**
     * 是否可搜索
     */
    boolean searchable() default true;

    /**
     * 是否可排序
     */
    boolean sortable() default true;
}
