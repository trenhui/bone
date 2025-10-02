package com.bone.core.extension;

import java.lang.annotation.*;

/**
 * ExtProvider
 *
 * @author renhui.trh 2023-10-30
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface ExtProvider {

    String tenantCode() default ExtPointConstants.DEFAULT_VALUE;

    String bizCode() default ExtPointConstants.DEFAULT_VALUE;

    String useCase() default ExtPointConstants.DEFAULT_VALUE;

    String scenario() default ExtPointConstants.DEFAULT_VALUE;

    String expression() default ExtPointConstants.EMPTY_STRING;
}

