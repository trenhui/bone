package com.bone.blueprint.infrastructure.annotation;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Capability {
    String name();
    String description();
    String inputSchema();
    String outputSchema();
    boolean idempotent() default false;
    int cost() default 1;           // 1-10，越高成本越大
    boolean retryable() default true;
    int timeout() default 30;       // 秒
}