package com.bone.core.context;

/**
 * 请求级上下文生命周期 SPI 契约。
 *
 * <p>用于解耦底层入站拦截器（Web/RPC）与上层各引擎（扩展引擎、审计、链路等）的线程级业务上下文绑定。
 */
public interface RequestContextLifecycle {

  /**
   * 初始化当前线程的请求上下文。
   *
   * @param tenantId 租户唯一标识
   * @param bizCode 业务场景标识
   */
  void initialize(String tenantId, String bizCode);

  /** 清理当前线程的上下文。必须保证幂等性与异常安全性。 */
  void clear();
}
