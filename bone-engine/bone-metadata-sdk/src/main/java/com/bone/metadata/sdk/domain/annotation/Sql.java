package com.bone.metadata.sdk.domain.annotation;

import com.bone.metadata.sdk.domain.enums.SqlTemplateType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Sql {
  String value() default "";

  SqlType type() default SqlType.AUTO;

  SqlTemplateType sqlTemplateType() default SqlTemplateType.MYBATIS;

  Class<?> resultType() default Void.class;
}
