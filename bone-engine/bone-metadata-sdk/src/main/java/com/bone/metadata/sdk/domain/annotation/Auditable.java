package com.bone.metadata.sdk.domain.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME) // 必须在运行时可见
@Target(ElementType.METHOD) // 只允许在方法上使用
public @interface Auditable {
  /** 审计操作描述（默认使用方法名） */
  String value() default "";
}
