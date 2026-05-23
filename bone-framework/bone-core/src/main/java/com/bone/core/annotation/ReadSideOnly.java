package com.bone.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记读侧查询 DSL（如 QueryBuilder、FluentQuery）。禁止出现在 {@code domain} 与
 * {@code application.command.handler} 包（DDD P0-5 / P0-6）。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ReadSideOnly {}
