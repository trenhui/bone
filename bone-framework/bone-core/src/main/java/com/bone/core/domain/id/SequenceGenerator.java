package com.bone.core.domain.id;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 自定义SequenceGenerator注解
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SequenceGenerator {
  String name();

  String sequenceName();

  int initialValue() default 1;

  int allocationSize() default 50;
}
