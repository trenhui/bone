package com.bone.metadata.sdk.support.dataSource;

import java.lang.annotation.*;

/**
 * 用于方法执行期间切换数据源的注解。
 *
 * <p>此注解可应用于类和方法，方法级别的注解优先于类级别的注解。 当执行方法时，系统会根据此注解自动切换到指定的数据源。
 *
 * @see DataSourceContextHolder
 * @see DataSourceAnnotationInterceptor
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface DataSourceSwitch {

  /**
   * 要使用的数据源名称。
   *
   * @return 数据源名称
   */
  String value() default "master";

  /**
   * 是否在事务上下文中强制使用指定的数据源。
   *
   * <p>此标志在复杂的事务场景中很有用，当您需要覆盖默认的事务数据源选择行为时。
   *
   * @return 如果即使在事务上下文中也应强制使用数据源，则返回true
   */
  boolean force() default false;
}
