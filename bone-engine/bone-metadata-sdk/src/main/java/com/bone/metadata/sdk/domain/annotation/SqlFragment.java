package com.bone.metadata.sdk.domain.annotation;

import java.lang.annotation.*;

/** 用于定义可重用的 SQL 片段，兼容 MyBatis 的 <sql> 标签。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(SqlFragment.List.class)
public @interface SqlFragment {
  String id();

  String value();

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  @interface List {
    SqlFragment[] value();
  }
}
