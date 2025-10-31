package com.bone.metadata.sdk.domain.annotation;

import com.bone.core.enums.Operator;

import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface QueryField {
    /**
     * 数据库字段名，默认为空表示使用Java字段名
     */
    String value() default "";

    /**
     * 查询操作符，默认为自动推断
     */
    Operator operator() default Operator.AUTO;

    /**
     * 是否忽略空值，默认为true
     */
    boolean ignoreNull() default true;

    /**
     * 是否忽略空字符串，默认为true
     */
    boolean ignoreEmpty() default true;

    /**
     * 是否启用模糊查询，默认为true
     */
    boolean fuzzy() default true;
}