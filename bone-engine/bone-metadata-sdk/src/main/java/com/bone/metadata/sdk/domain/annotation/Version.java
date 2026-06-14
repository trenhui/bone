package com.bone.metadata.sdk.domain.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 用于标识实体类的版本字段 */
@Target(ElementType.FIELD) // 适用于字段
@Retention(RetentionPolicy.RUNTIME) // 运行时可访问
public @interface Version {
  // 没有额外属性，仅用于标识版本字段
}
