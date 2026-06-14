package com.bone.metadata.sdk.domain.annotation;

import java.lang.annotation.*;

/**
 * The annotation to configure the mapping from a class to a database table.
 *
 * @author 梅山 2023-10-1
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Inherited
public @interface Table {
  String value() default "";
}
