package com.bone.blueprint.application.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 可选：为 Flow/AI 等编排暴露的元数据（§20）。须与 {@code com.bone.blueprint.application} 同层，避免 application 依赖
 * infrastructure。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Capability {

  String name();

  String description();

  String inputSchema();

  String outputSchema();

  boolean idempotent();

  int cost();

  boolean retryable();

  int timeout();
}
