package com.bone.core.web.config;

import com.bone.core.idempotency.IdempotencyService;
import com.bone.core.idempotency.IdempotencyStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 幂等编排服务 {@link IdempotencyService} 的统一注册入口。
 *
 * <p><b>修的是什么缺陷</b>：{@code IdempotencyService} 位于 {@code com.bone.core.idempotency}，而业务模块的
 * {@code @ComponentScan} 一律是白名单式（只列 {@code com.bone.<module>} 与少数 core 子包）——它不在任何模块的扫描域内， 于是 {@code
 * OrderController} 的构造注入拿不到 Bean：blueprint 实机复现为 {@code orderController} 装配失败、
 * <strong>应用起不来</strong>（与 {@link BoneWebExceptionAutoConfiguration} 记下的 {@code
 * com.bone.core.exception.GlobalExceptionHandler} 是同一型缺陷：core 组件无人注册即死代码）。
 *
 * <p><b>为什么用自动配置而不是让模块扫包</b>：core 组件的注册入口应当唯一且由 framework 兜底，模块侧<strong>无需任何 {@code @ComponentScan}
 * 或 {@code @Import}</strong>——否则每接入一个 core 能力都要改一次启动类，漏一个就是上线才炸的启动失败。
 *
 * <p><b>为什么以 {@code @ConditionalOnBean(IdempotencyStore)} 为条件</b>：存储契约由业务模块的 infrastructure
 * 提供（MySQL/Redis/Mongo 皆可）。只有真正接入幂等、已存在 {@link IdempotencyStore} 实现的模块才需要本服务； 未接入的模块不会额外产生
 * Bean，行为与接入前一致。
 *
 * <p><b>落位说明</b>：本自动配置随 {@code bone-web} 分发——它是 framework 跨切面自动配置的唯一宿主（统一异常处理器同在此处， {@code
 * bone-core} 不引入 {@code spring-boot-autoconfigure}）。因此接入幂等的模块需依赖 {@code bone-web} （blueprint
 * 即如此）；不依赖该模块、又实现了 {@link IdempotencyStore} 的上下文拿不到本服务，届时把它下沉到同样宿主的 自动配置里即可。
 *
 * <p><b>注册方式</b>：见 {@code
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}。
 */
@AutoConfiguration
@ConditionalOnBean(IdempotencyStore.class)
public class IdempotencyAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public IdempotencyService idempotencyService(IdempotencyStore store) {
    return new IdempotencyService(store);
  }
}
