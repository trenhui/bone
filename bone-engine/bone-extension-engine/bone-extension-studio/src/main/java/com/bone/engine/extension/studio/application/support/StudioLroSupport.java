package com.bone.engine.extension.studio.application.support;

import com.bone.core.model.ProblemDetail;
import com.bone.core.threadlocal.TransmittableThreadLocal;
import com.bone.engine.extension.studio.application.ExtensionCommandApplicationService;
import com.bone.engine.extension.studio.application.ExtensionQueryApplicationService;
import com.bone.engine.extension.studio.common.StudioErrorCodes;
import com.bone.engine.extension.studio.config.StudioRequestContextFilter;
import com.bone.engine.extension.studio.domain.model.extension.Extension;
import com.bone.engine.extension.studio.domain.model.operation.StudioOperation;
import com.bone.engine.extension.studio.observability.StudioExtensionMetrics;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * 插件部署等 LRO（进程内；生产可换 Redis/DB）。
 *
 * <p><b>2026-10-08 修复无界 Map 内存泄漏</b>：{@code operations} 原为只增不减的 {@link ConcurrentHashMap}， 完成的
 * operation <b>永不移除</b>，每次 {@link #startPluginDeploy} 都永久驻留一条 （含 result map / error ProblemDetail）⇒
 * 长期运行必然 OOM。此前的修复（含构造器 {@code completedTtlMillis} 与 {@code @PreDestroy
 * destroy()}）在某次批量提交中被整体回退，连带{@code StudioLroSupportTest} 编译失败 （测试期望 5 参构造 + {@code
 * destroy()}）——**测试是正确的一方，它锁定的正是这个泄漏**。 现恢复并加固回收能力：
 *
 * <ul>
 *   <li>构造器 {@code completedTtlMillis}：操作完成（{@code isDone()}）后保留多久，供客户端轮询到结果； <b>必须 &gt; 0</b>，否则
 *       {@code <= 0} 语义歧义（0 到底是"立刻删"还是"永不过期"）。
 *   <li>{@link #evictCompletedOperations()}：按 TTL 扫描回收，测试用 {@code TTL=0} 驱动即刻回收。
 *   <li>{@code @PreDestroy destroy()}：关线程池 + 停调度，容器停机时不泄漏线程。
 * </ul>
 */
@Service
public class StudioLroSupport {

  private static final String TYPE_PLUGIN_DEPLOY = "plugin.deploy";

  /** 回收扫描周期（毫秒）。 */
  private static final long EVICT_INTERVAL_MILLIS = 30_000L;

  /** TTL=0 时「完成后触发回收」的延迟（毫秒）——必须大于调用方轮询间隔、小于其超时， 使「先观察到完成态、再观察到消失」这一契约可被稳定观察（见 runDeploy 内注释）。 */
  private static final long EVICT_ON_COMPLETE_DELAY_MILLIS = 500L;

  /** 完成后 operation 的默认保留时长（毫秒）：够客户端轮询一轮，又不无限驻留。 */
  private static final long DEFAULT_COMPLETED_TTL_MILLIS = 300_000L;

  private final ExtensionCommandApplicationService extensionCommandHandler;
  private final ExtensionQueryApplicationService extensionQueryHandler;
  private final StudioAuditSupport auditService;
  private final StudioExtensionMetrics studioMetrics;
  private final Map<String, StudioOperation> operations = new ConcurrentHashMap<>();

  /** 完成后保留时长（毫秒），<=0 表示"完成后立即可回收"。 */
  private final long completedTtlMillis;

  private final ExecutorService executor =
      Executors.newCachedThreadPool(
          r -> {
            Thread t = new Thread(r, "studio-lro");
            t.setDaemon(true);
            return t;
          });
  private final ScheduledExecutorService evictor =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "studio-lro-evictor");
            t.setDaemon(true);
            return t;
          });

  /**
   * 生产用构造器：默认 TTL + 自动调度回收。
   *
   * <p>⚠️ <b>必须显式标 {@code @Autowired}</b>：本类有两个构造器（生产 4 参 / 单测 5 参）， 无标注时 Spring
   * <b>无法判定注入哪个</b>，启动直接失败： {@code Failed to instantiate [StudioLroSupport]: No default constructor
   * found} （实测 22 个 @SpringBootTest 全部连带挂掉，根因就是这一处）。 记忆铁律：<b>一旦引入多构造器，注入歧义必须显式消解</b>，否则整个应用上下文起不来。
   */
  @org.springframework.beans.factory.annotation.Autowired
  public StudioLroSupport(
      ExtensionCommandApplicationService extensionCommandHandler,
      ExtensionQueryApplicationService extensionQueryHandler,
      StudioAuditSupport auditService,
      StudioExtensionMetrics studioMetrics) {
    this(
        extensionCommandHandler,
        extensionQueryHandler,
        auditService,
        studioMetrics,
        DEFAULT_COMPLETED_TTL_MILLIS);
  }

  /**
   * 指定完成后 TTL 的构造器（单测用 {@code 0} 驱动即刻回收）。
   *
   * <p>负值归一到 0（等价"完成即可回收"），避免 {@code Instant + 负数} 落在过去而永不回收以外的歧义。
   */
  public StudioLroSupport(
      ExtensionCommandApplicationService extensionCommandHandler,
      ExtensionQueryApplicationService extensionQueryHandler,
      StudioAuditSupport auditService,
      StudioExtensionMetrics studioMetrics,
      long completedTtlMillis) {
    this.extensionCommandHandler = extensionCommandHandler;
    this.extensionQueryHandler = extensionQueryHandler;
    this.auditService = auditService;
    this.studioMetrics = studioMetrics;
    this.completedTtlMillis = Math.max(0L, completedTtlMillis);
    this.evictor.scheduleWithFixedDelay(
        this::evictCompletedOperations,
        EVICT_INTERVAL_MILLIS,
        EVICT_INTERVAL_MILLIS,
        TimeUnit.MILLISECONDS);
  }

  public String startPluginDeploy(Long pluginId) {
    String operationId = "op-" + UUID.randomUUID().toString().replace("-", "");
    StudioOperation op = new StudioOperation();
    op.setOperationId(operationId);
    op.setType(TYPE_PLUGIN_DEPLOY);
    op.setResourceId(pluginId);
    op.setDone(false);
    op.setProgress(0);
    op.setCreatedAt(Instant.now());
    operations.put(operationId, op);
    studioMetrics.recordLro(TYPE_PLUGIN_DEPLOY, "accepted");

    Map<String, String> mdc = MDC.getCopyOfContextMap();
    executor.submit(
        TransmittableThreadLocal.wrap(
            () -> {
              if (mdc != null) {
                MDC.setContextMap(mdc);
              }
              runDeploy(operationId, pluginId);
              MDC.clear();
            }));
    return operationId;
  }

  public StudioOperation getOperation(String operationId) {
    return operations.get(operationId);
  }

  private void runDeploy(String operationId, Long pluginId) {
    StudioOperation op = operations.get(operationId);
    if (op == null) {
      return;
    }
    try {
      op.setProgress(20);
      extensionCommandHandler.deployExtension(pluginId);
      op.setProgress(90);
      Extension extension = extensionQueryHandler.findExtensionById(pluginId);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", extension != null ? extension.getId() : pluginId);
      result.put("enabled", extension != null && extension.isEnabled());
      op.setResult(result);
      op.setProgress(100);
      op.setDone(true);
      op.setCompletedAt(Instant.now());
      auditService.success("plugin.deploy", "plugin", String.valueOf(pluginId));
      studioMetrics.recordLro(TYPE_PLUGIN_DEPLOY, "success");
      studioMetrics.recordDeploy("deploy", "success");
    } catch (Exception ex) {
      ProblemDetail detail = ProblemDetail.of(StudioErrorCodes.STATE_INVALID, 409, ex.getMessage());
      String traceId = MDC.get(StudioRequestContextFilter.TRACE_ID);
      detail.setTraceId(traceId);
      op.setError(detail);
      op.setDone(true);
      op.setProgress(100);
      op.setCompletedAt(Instant.now());
      auditService.failure("plugin.deploy", "plugin", String.valueOf(pluginId), ex.getMessage());
      studioMetrics.recordLro(TYPE_PLUGIN_DEPLOY, "failure");
      studioMetrics.recordDeploy("deploy", "failure");
    }
    // TTL=0（及已过期者）在此**即时**触发回收，不必等 30s 扫描周期：
    // 「TTL=0 意为完成即可回收」是本类的公开契约（StudioLroSupportTest 依赖它），
    // 若只靠定时扫描，完成后到下一次扫描之间 Map 仍会持有已完成的 operation。
    //
    // ⚠️ 回收必须**延迟一小段**再执行，不能在 runDeploy 工作线程内同步做、也不能零延迟异步做：
    // 调用方（轮询 GET /operations/{id}）需要存在一个可观察窗口——先看到「done=true 的结果」，
    // 再看到「已回收」。若回收快于一次轮询间隔，调用方将永远观察不到完成态（只会拿到 null），
    // 契约「完成即可查、随后消失」就退化成「查不到」。
    // D 取值约束：> 一次轮询间隔（客户端典型 50ms 级）且 << 调用方超时（典型 5s 级）；
    // 取 500ms 落在两者之间并留足余量，使该行为在慢机器上也稳定可复现。
    if (completedTtlMillis <= 0) {
      evictor.schedule(
          this::evictCompletedOperations, EVICT_ON_COMPLETE_DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }
  }

  /**
   * 回收已「完成且超过 TTL」的 operation —— 这是 {@link ConcurrentHashMap} 不无界增长的关键。
   *
   * <p>判据用 {@code isDone() && completedAt 存在}：{@code runDeploy} 的正常与异常分支都会打上 {@code done=true} +
   * {@code completedAt}，故不会误删仍在执行中的 operation。
   *
   * <p>用 {@code remove(id, op)}（CAS 语义）而非 {@code remove(id)}：并发下若该槽位已被新operation
   * 复用，只删自己读到的那一个，不会误删别人的。
   */
  void evictCompletedOperations() {
    // 保留窗口截止点：completedAt <= now - TTL 即视为过期（TTL=0 时"完成即可回收"）
    Instant deadline = Instant.now().minusMillis(completedTtlMillis);
    operations
        .entrySet()
        .removeIf(
            e -> {
              StudioOperation op = e.getValue();
              if (op == null || !op.isDone() || op.getCompletedAt() == null) {
                return false;
              }
              return !op.getCompletedAt().isAfter(deadline) && operations.remove(e.getKey(), op);
            });
  }

  /** 当前驻留的 operation 数（可观测/测试用）。 */
  public int trackedOperationCount() {
    return operations.size();
  }

  /** 容器停机：停调度 + 关线程池，避免线程泄漏。 */
  @PreDestroy
  public void destroy() {
    evictor.shutdownNow();
    executor.shutdownNow();
  }
}
