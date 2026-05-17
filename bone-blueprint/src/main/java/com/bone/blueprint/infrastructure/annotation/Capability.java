package com.bone.blueprint.infrastructure.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 兼容存量 Handler 的编排元数据注解（与 {@code application.annotation.Capability} 字段一致）。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Capability {

    String name();

    String description();

    String inputSchema();

    String outputSchema();

    boolean idempotent() default false;

    int cost() default 1;

    boolean retryable() default false;

    int timeout() default 30;
}
