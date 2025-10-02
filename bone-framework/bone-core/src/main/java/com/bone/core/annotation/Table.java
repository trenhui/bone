package com.bone.core.annotation;

import java.lang.annotation.*;

/**
 * The annotation to configure the mapping from a class to a database table.
 *
 * @author Kazuki Shimizu
 * @author Bastian Wilhelm
 * @author Mikhail Polivakha
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Inherited
public @interface Table {
    String value() default "";
}
