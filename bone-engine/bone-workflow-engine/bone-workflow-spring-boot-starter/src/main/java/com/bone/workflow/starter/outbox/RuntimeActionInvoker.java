package com.bone.workflow.starter.outbox;

import java.util.Map;

/**
 * 平台动作调用端口（窄接口）。
 *
 * <p>状态投影执行器只关心「把一条意图翻译成一次平台动作调用」，不应直接耦合 {@code RuntimeActionApplicationService} 这个重应用服务——其 {@code
 * invokeAction} 受框架代理 / 非公有父类约束， 在单测中难以打桩（Mockito 无法 stub 非公有父类声明的方法）。本端口把耦合收敛到一处方法引用，
 * 既是六边形「依赖端口而非实现」的体现，也让执行器可独立单测、回归幂等键等关键契约。
 *
 * <p>方法签名与 {@code RuntimeActionApplicationService#invokeAction} 对齐（含 primitive {@code long
 * tenantId}）， 宿主装配时以 {@code actionService::invokeAction} 直接绑定；返回值的差异（应用服务返回结果 Map，本端口消费侧忽略） 由 Java
 * 方法引用「返回值可丢弃」规则自然抹平。
 */
@FunctionalInterface
public interface RuntimeActionInvoker {

  void invoke(
      String entityCode,
      long tenantId,
      String recordId,
      String actionCode,
      Map<String, Object> params,
      String operatorId,
      String idempotencyKey,
      Integer expectedVersion);
}
