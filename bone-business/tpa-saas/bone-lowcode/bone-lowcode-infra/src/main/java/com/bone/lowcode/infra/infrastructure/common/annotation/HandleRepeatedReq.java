package com.bone.lowcode.infra.infrastructure.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface HandleRepeatedReq {

    boolean value() default false; // redis的key是否需要拼接参数值
}
