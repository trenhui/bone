package com.bone.core.usecase;

import java.lang.annotation.*;

/**
 * @deprecated since DDD v3.4/v3.6 — AI/Flow 能力发现请用 {@code @Capability}。计划 2026-12-31
 *     删除。
 */
@Deprecated(forRemoval = true, since = "3.6")
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface UseCase {
    String name();
    String description() default "";
    boolean transactional() default true;
    boolean idempotent() default false;
}
