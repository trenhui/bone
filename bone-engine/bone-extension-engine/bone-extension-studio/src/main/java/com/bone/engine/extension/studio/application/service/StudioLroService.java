package com.bone.engine.extension.studio.application.service;

import com.bone.core.model.ProblemDetail;
import com.bone.engine.extension.studio.application.command.handler.ExtensionCommandApplicationService;
import com.bone.engine.extension.studio.application.query.handler.ExtensionQueryApplicationService;
import com.bone.engine.extension.studio.common.StudioErrorCodes;
import com.bone.engine.extension.studio.config.StudioRequestContextFilter;
import com.bone.engine.extension.studio.domain.model.extension.Extension;
import com.bone.engine.extension.studio.domain.model.operation.StudioOperation;
import com.bone.engine.extension.studio.observability.StudioExtensionMetrics;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/** 插件部署等 LRO（进程内；生产可换 Redis/DB）。 */
@Service
public class StudioLroService {

  private static final String TYPE_PLUGIN_DEPLOY = "plugin.deploy";

  private final ExtensionCommandApplicationService extensionCommandHandler;
  private final ExtensionQueryApplicationService extensionQueryHandler;
  private final StudioAuditService auditService;
  private final StudioExtensionMetrics studioMetrics;
  private final Map<String, StudioOperation> operations = new ConcurrentHashMap<>();
  private final ExecutorService executor =
      Executors.newCachedThreadPool(
          r -> {
            Thread t = new Thread(r, "studio-lro");
            t.setDaemon(true);
            return t;
          });

  public StudioLroService(
      ExtensionCommandApplicationService extensionCommandHandler,
      ExtensionQueryApplicationService extensionQueryHandler,
      StudioAuditService auditService,
      StudioExtensionMetrics studioMetrics) {
    this.extensionCommandHandler = extensionCommandHandler;
    this.extensionQueryHandler = extensionQueryHandler;
    this.auditService = auditService;
    this.studioMetrics = studioMetrics;
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
        () -> {
          if (mdc != null) {
            MDC.setContextMap(mdc);
          }
          runDeploy(operationId, pluginId);
          MDC.clear();
        });
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
  }
}
