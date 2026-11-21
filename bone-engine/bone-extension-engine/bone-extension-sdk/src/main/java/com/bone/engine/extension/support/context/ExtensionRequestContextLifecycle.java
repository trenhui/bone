package com.bone.engine.extension.support.context;

import com.bone.core.context.RequestContextLifecycle;
import lombok.extern.slf4j.Slf4j;

/**
 * 业务扩展引擎上下文生命周期实现。
 *
 * <p>通过 {@link RequestContextLifecycle} SPI 对接入站请求链路，将租户与业务编码绑定到 {@link
 * ExtensionContextManager}，并在请求结束时对称释放。
 *
 * <p><b>为何持有 ExtensionScope 而非直接 clearContext()</b>：{@code with()} 返回的 {@link ExtensionScope}
 * 保存了旧上下文，其 {@code close()} 负责恢复旧上下文并清理 ThreadLocal；丢弃返回值再单独清空会破坏这一对称语义
 * （嵌套或线程池复用场景下会错误丢失外层上下文）。本实现为单例 Bean，故用 {@link ThreadLocal} 保存当前线程的 Scope，保证线程隔离与幂等清理。
 *
 * <p>本类由 {@code ExtensionAutoConfiguration} 以 {@code @Bean} 方式装配，刻意不加 {@code @Component}：
 * 组件扫描会绕过自动配置上的 {@code @ConditionalOnProperty(bone.extension.enabled)}，导致扩展被关闭时 SPI 仍被注册。
 */
@Slf4j
public class ExtensionRequestContextLifecycle implements RequestContextLifecycle {

  private static final ThreadLocal<ExtensionScope> SCOPE_HOLDER = new ThreadLocal<>();

  @Override
  public void initialize(String tenantId, String bizCode) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId must not be null or blank");
    }
    // 防御：线程池复用时若残留上次未关闭的 Scope，先关闭，避免恢复陈旧上下文
    closeScope();
    try {
      ExtensionScope scope = ExtensionContextManager.with(tenantId, bizCode);
      SCOPE_HOLDER.set(scope);
      log.debug("ExtensionContext initialized: tenantId={}, bizCode={}", tenantId, bizCode);
    } catch (Exception e) {
      // 初始化失败必须回滚，绝不残留半初始化上下文（fail-closed 交由拦截器统一处理）
      closeScope();
      ExtensionContextManager.clearContext();
      log.error(
          "Failed to initialize ExtensionContext: tenantId={}, bizCode={}", tenantId, bizCode, e);
      throw e;
    }
  }

  @Override
  public void clear() {
    ExtensionScope scope = SCOPE_HOLDER.get();
    if (scope == null) {
      // 无 Scope 可关闭（initialize 未成功或已清理）：兜底清理，杜绝 ThreadLocal 残留
      ExtensionContextManager.clearContext();
      return;
    }
    try {
      // 唯一清理通道：Scope.close() 自身会 remove 并恢复 oldContext，不得再叠加 clearContext() 冲掉恢复结果
      scope.close();
    } finally {
      SCOPE_HOLDER.remove();
    }
  }

  /** 关闭并移除当前线程的 Scope，保证幂等；ThreadLocal 必须 remove 以防线程池场景内存泄漏。 */
  private void closeScope() {
    ExtensionScope scope = SCOPE_HOLDER.get();
    if (scope == null) {
      return;
    }
    try {
      scope.close();
    } finally {
      SCOPE_HOLDER.remove();
    }
  }
}
