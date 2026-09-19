package com.bone.core.tenant.context;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 在指定租户上下文内执行一段逻辑——异步入口声明「以哪个租户执行」的唯一入口。
 *
 * <p><b>为什么必须有它</b>：{@link TenantContext} 只覆盖请求线程（由 JWT Filter / {@code TenantInterceptor}
 * 写入）。定时任务、Outbox 中继、MQ 消费者等线程没有请求上下文，而 SDK 写路径（ADR-0029）对租户表是<strong>失败关闭</strong>的 ——{@code save}
 * / {@code update} 取不到租户即抛 {@code MissingTenantContextException}，且写路径既没有 caller-EQ 兜底、也不认 {@code
 * disableTenantFilter()}。这类入口只能显式声明租户。
 *
 * <p><b>为什么恢复而不是无条件清空</b>：异步线程的进入前上下文通常为 {@code null}（等价于清空，不会在复用线程池时串租户）；
 * 但若调用方已在某个租户内（如请求线程内的同步调用、嵌套 {@code runAs}），恢复比清空更不易误伤。
 *
 * <p><b>注意线程继承语义</b>：{@link TenantContext} 底层是 {@code
 * InheritableThreadLocal}，作用域内<strong>新建</strong>的线程会 继承本租户，而本类退出时的恢复只作用于当前线程。需要向工作线程传递上下文时，请显式
 * {@code TransmittableThreadLocal.wrap(...)} / 线程池包装，或在工作线程内再次 {@code runAs}。
 *
 * <p>典型用法（全租户扫描后逐行写）：
 *
 * <pre>{@code
 * for (OrderHeadProjection row : expired) {
 *   TenantContextRunner.runAs(row.getTenantId(), () -> service.cancel(command));
 * }
 * }</pre>
 */
public final class TenantContextRunner {

  private TenantContextRunner() {
    // 工具类，禁止实例化
  }

  /** 以 {@code tenantId} 为当前租户执行 {@code action}；执行结束后恢复进入前的上下文。 */
  public static void runAs(Long tenantId, Runnable action) {
    callAs(
        tenantId,
        () -> {
          action.run();
          return null;
        });
  }

  /**
   * 以 {@code tenantId} 为当前租户执行 {@code action} 并返回其结果；执行结束后恢复进入前的上下文。
   *
   * @throws NullPointerException {@code tenantId} 为 {@code null}——"无租户"不等于"以某个租户执行"，这里快速失败，
   *     而不是静默退化成无上下文（那会把问题推迟到底层的 {@code MissingTenantContextException}，现场信息更少）
   */
  public static <T> T callAs(Long tenantId, Supplier<T> action) {
    Objects.requireNonNull(tenantId, "tenantId must not be null: 异步入口必须显式声明租户（ADR-0031 D3）");
    String previous = TenantContext.getTenantId();
    try {
      TenantContext.setTenantId(tenantId);
      return action.get();
    } finally {
      TenantContext.setTenantId(previous);
    }
  }
}
