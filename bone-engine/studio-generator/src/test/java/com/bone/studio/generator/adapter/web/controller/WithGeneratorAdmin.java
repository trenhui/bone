package com.bone.studio.generator.adapter.web.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * 契约测试的认证上下文：授予 generator 全部写端点权限码。
 *
 * <p>{@code SecurityConfig} 已启用 {@code @EnableMethodSecurity}，控制器上的 {@code @PreAuthorize} 真正生效，
 * 因此访问这些端点需携带对应 authority。集中为一个组合注解，避免每个测试类重复罗列权限码，也避免权限码调整时 漏改——新增端点权限时只需改这一处。与 masterdata /
 * system 的同类做法一致。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@WithMockUser(
    authorities = {
      "generator:admin:write",
      "generator:codegen:write",
      "generator:datasources:sync",
      "generator:datasources:write",
      "generator:templates:write"
    })
public @interface WithGeneratorAdmin {}
