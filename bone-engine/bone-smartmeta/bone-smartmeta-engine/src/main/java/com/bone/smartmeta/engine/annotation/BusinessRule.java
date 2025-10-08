package com.bone.smartmeta.engine.annotation;

import java.lang.annotation.*;

/**
 * 业务规则注解，用于定义字段级业务规则
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BusinessRule {

    /**
     * 规则名称
     */
    String name();

    /**
     * 规则表达式
     */
    String expression();

    /**
     * 错误消息
     */
    String errorMessage();

    /**
     * 规则严重程度
     */
    String severity() default "ERROR";
}
