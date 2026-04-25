package com.bone.core.usecase;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface UseCase {
    String name();
    String description() default "";
    boolean transactional() default true;
    boolean idempotent() default false;
}
