package com.bone.metadata.sdk.domain.annotation;

import com.bone.metadata.sdk.domain.enums.EnumType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 自定义枚举持久化策略注解 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Enumerated {
  /** 指定枚举值的存储方式 */
  EnumType value() default EnumType.ORDINAL;
}
