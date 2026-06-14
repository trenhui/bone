package com.bone.core.domain.id;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 自定义IdGeneration注解
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GeneratedValue {
  GenerationStrategy strategy() default GenerationStrategy.IDENTITY;

  String generator() default ""; // 用于指定自定义生成器，例如类名
}
