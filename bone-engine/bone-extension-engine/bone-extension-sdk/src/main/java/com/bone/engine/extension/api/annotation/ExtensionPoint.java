package com.bone.engine.extension.api.annotation;

import java.lang.annotation.*;

/**
 * 标记接口为扩展点
 *
 * <p>框架会为标记的接口创建动态代理，实现业务逻辑与扩展实现的解耦。
 *
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExtensionPoint {

  /** 扩展点名称 */
  String name() default "";

  /** 扩展点描述 */
  String description() default "";

  /** 扩展点版本 */
  String version() default "1.0.0";

  /** 是否启用事务 */
  boolean transactional() default false;

  /** 默认超时时间（秒） */
  int timeout() default 30;

  /** 是否单例模式 */
  boolean singleton() default true;
}
