package com.bone.core.capability;

import java.lang.annotation.*;

/**
 * 能力声明：把「可被 Flow / AI 编排发现」的用例标注出来。
 *
 * <p>可标在类上（存量 Handler 形态）或方法上（Application Service First 形态，见 ADR-0028 / AS-01：用例方法即能力，避免为承载元数据而在
 * ApplicationService 之上再套同义 Handler）。 两种形态由 {@link HandlerRegistry} 统一注册，对 {@code /capabilities}
 * 发现端点的输出无差异。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Capability {
  String name();

  String description();

  String inputSchema();

  String outputSchema();

  boolean idempotent() default false;

  int cost() default 1;

  boolean retryable() default true;

  int timeout() default 30;
}
