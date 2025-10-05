package com.bone.metadata.sdk.domain.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于标识实体类的主键字段
 * @author 梅山 2023-10-1
 */
@Target(ElementType.FIELD)  // 适用于字段
@Retention(RetentionPolicy.RUNTIME)  // 运行时可访问
public @interface Id {
    // 没有额外属性，仅用于标识主键
}
