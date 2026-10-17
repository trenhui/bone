package com.bone.metadata.runtime.application;

import com.bone.metadata.runtime.domain.RuntimeActionContext;
import java.util.Map;

/**
 * 逃逸动作处理器（SPI）。
 *
 * <p>状态机边的「状态流转」由平台统一负责（{@code RuntimeActionApplicationService} 调 {@code
 * JdbcRuntimeRecordService.update} 写 {@code to_status}），本接口只负责<strong>副作用</strong>
 * （如调外部支付网关、发消息）。平台据此把「普通状态推进」与「有副作用的外部调用」彻底分离， 逃逸动作可独立做半事务/补偿/幂等治理（Runtime 数据面设计 §4.8.1）。
 */
public interface RuntimeActionHandler {

  /**
   * 执行逃逸动作的副作用。
   *
   * @param ctx 动作上下文（含记录 id、入参、操作者）
   * @return 副作用结果（如支付流水号），随执行日志与响应一并返回
   */
  Map<String, Object> execute(RuntimeActionContext ctx);
}
