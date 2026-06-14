package com.bone.metadata.sdk.support.dataSource.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 数据源注解 - 用于标记方法或类使用的数据源 优先级：方法级注解 > 类级注解 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface DS {
  /**
   * 数据源名称
   *
   * @return 数据源名称
   */
  String value() default "master";
}
