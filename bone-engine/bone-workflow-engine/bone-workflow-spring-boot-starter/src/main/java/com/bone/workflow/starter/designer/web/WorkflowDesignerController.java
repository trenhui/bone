package com.bone.workflow.starter.designer.web;

import com.bone.workflow.starter.designer.WorkflowDefinitionCommandService;
import com.bone.workflow.starter.designer.WorkflowSimulationService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作流设计器 REST 层（v1.5）。
 *
 * <p><b>挂载方式</b>：本控制器由 {@link WorkflowDesignerWebAutoConfiguration} 在「存在 Servlet Web 上下文」时 才注册为
 * bean——所以 starter 不依赖任何宿主应用，宿主只要引了本 starter 且带 web，设计器端点自动可用；无 web 的宿主（纯领域库场景） 不会加载它，也不会因缺
 * spring-web 而启动失败。
 *
 * <p><b>操作人</b>：写路径（保存 / 发布 / 上下线）从请求头 {@code X-Operator-Id} 取操作人，缺则 Spring 直接 400——写路径审计列必须回答
 * 「谁改的」，不接受匿名写入（与命令服务 {@code requireOperator} 同口径）。
 *
 * <p><b>租户</b>：命令服务从 {@code TenantContext} 取租户，由宿主的租户解析过滤器注入（本 starter 不内置）；租户缺失时命令服务抛 {@link
 * IllegalStateException}，由 {@link WorkflowDesignerExceptionAdvice} 映射为 400。
 *
 * <p><b>响应信封</b>：统一 {@link ApiResponse}，对齐骨核 {@code ApiResponse{code,message,data}} 与前端解包约定。
 */
@RestController
@RequestMapping("/api/v1/workflow/definitions")
public class WorkflowDesignerController {

  private final WorkflowDefinitionCommandService command;
  private final WorkflowSimulationService simulation;

  public WorkflowDesignerController(
      WorkflowDefinitionCommandService command, WorkflowSimulationService simulation) {
    this.command = command;
    this.simulation = simulation;
  }

  /** 只校验不落库：给设计器边改边看。请求体即流程定义原文（与保存/发布同口径）。 */
  @PostMapping("/validate")
  public ApiResponse<WorkflowDefinitionCommandService.ValidationReport> validate(
      @RequestBody String definitionJson) {
    return ApiResponse.ok(command.validate(definitionJson));
  }

  /** 定义总览：每个 key 的当前生效版本。 */
  @GetMapping
  public ApiResponse<List<WorkflowDefinitionCommandService.Published>> listLatest() {
    return ApiResponse.ok(command.listLatest());
  }

  /** 取草稿（无草稿返回 {@code data=null}，前端据此判定「新建」）。 */
  @GetMapping("/{key}/draft")
  public ApiResponse<WorkflowDefinitionCommandService.Draft> findDraft(@PathVariable String key) {
    return ApiResponse.ok(command.findDraft(key).orElse(null));
  }

  /** 保存草稿（新建或覆盖，覆盖需 {@code expectedRevision}）。 */
  @PostMapping("/{key}/draft")
  public ApiResponse<WorkflowDefinitionCommandService.SaveDraftResult> saveDraft(
      @PathVariable String key,
      @RequestHeader("X-Operator-Id") String operatorId,
      @RequestBody SaveDraftRequest request) {
    return ApiResponse.ok(
        command.saveDraft(key, request.definitionJson(), request.expectedRevision(), operatorId));
  }

  /** 丢弃草稿（物理删除）。 */
  @DeleteMapping("/{key}/draft")
  public ApiResponse<Void> discardDraft(
      @PathVariable String key, @RequestHeader("X-Operator-Id") String operatorId) {
    command.discardDraft(key);
    return ApiResponse.ok(null);
  }

  /** 发布草稿为不可变版本（fail-closed：有违规 422）。 */
  @PostMapping("/{key}/publish")
  public ApiResponse<WorkflowDefinitionCommandService.Published> publish(
      @PathVariable String key,
      @RequestHeader("X-Operator-Id") String operatorId,
      @RequestBody PublishRequest request) {
    return ApiResponse.ok(command.publish(key, request.expectedRevision(), operatorId));
  }

  /** 上线 / 下线某版本。 */
  @PostMapping("/{key}/enabled")
  public ApiResponse<WorkflowDefinitionCommandService.Published> setEnabled(
      @PathVariable String key,
      @RequestHeader("X-Operator-Id") String operatorId,
      @RequestBody EnabledRequest request) {
    return ApiResponse.ok(
        command.setEnabled(key, request.version(), request.enabled(), operatorId));
  }

  /** 某 key 的全部版本（含已下线）。 */
  @GetMapping("/{key}/versions")
  public ApiResponse<List<WorkflowDefinitionCommandService.Published>> listVersions(
      @PathVariable String key) {
    return ApiResponse.ok(command.listVersions(key));
  }

  /** 指定版本（含 {@code ui} 段原文，供设计器还原版式）；不存在返回 404。 */
  @GetMapping("/{key}/versions/{version}")
  public ResponseEntity<ApiResponse<WorkflowDefinitionCommandService.Published>> findVersion(
      @PathVariable String key, @PathVariable int version) {
    return command
        .findVersion(key, version)
        .map(published -> ResponseEntity.ok(ApiResponse.ok(published)))
        .orElse(
            ResponseEntity.status(404)
                .body(ApiResponse.of(404, "流程定义 " + key + " 不存在版本 " + version, null)));
  }

  /** 发布前试运行：路径 + 分支判定 + 每节点审批人解析。 */
  @PostMapping("/simulate")
  public ApiResponse<WorkflowSimulationService.SimulationReport> simulate(
      @RequestBody SimulateRequest request) {
    return ApiResponse.ok(
        simulation.simulate(
            request.definitionJson(),
            request.sampleData(),
            request.initiatorId(),
            request.entityCode(),
            request.recordId()));
  }
}
