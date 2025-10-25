package com.bone.metadata.sdk.support.dataSource;

import java.lang.annotation.*;

/**
 * Annotation that specifies the data source to be used at method or class level.
 * <p>
 * This annotation can be applied to both classes and methods, with method-level annotations
 * taking precedence over class-level annotations. By default, the "master" data source
 * is specified if no explicit value is provided.
 * </p>
 * 
 * @see DataSourceContextHolder
 * @see DataSourceAnnotationInterceptor
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface DataSource {
    
    /**
     * Specifies the name of the data source to use.
     * 
     * @return the name of the data source
     */
    String value() default "master";
}