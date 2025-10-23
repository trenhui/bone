package com.bone.metadata.sdk.support.dataSource;

import java.lang.annotation.*;

/**
 * 数据源切换注解
 * 可用于类或方法上，方法级别优先级高于类级别
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface DS {
    
    /**
     * 数据源名称
     * @return 数据源名称
     */
    String value() default "master";
    
    /**
     * 是否在事务中强制使用指定数据源
     * 在某些复杂事务场景下，可能需要强制切换数据源
     * @return 是否强制使用
     */
    boolean force() default false;
}