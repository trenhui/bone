package com.bone.workflow.starter.designer.web;

import com.bone.workflow.starter.designer.WorkflowDefinitionCommandService;
import com.bone.workflow.starter.designer.WorkflowDefinitionRequestException;
import com.bone.workflow.starter.designer.WorkflowDesignerAction;
import com.bone.workflow.starter.designer.WorkflowDesignerAuthorizer;
import com.bone.workflow.starter.designer.WorkflowSimulationService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作流设计器 REST 层（v1.5）。
 *
 * <p><b>挂载方式</b>：本控制器由 {@link WorkflowDesignerWebAutoConfiguration} 在「存在 Servlet Web 上下文」时 才注册为
 * bean——所以 starter 不依赖任何宿主应用，宿主只要引了本 starter 且带 web，设计器端点自动可用；无 web 的宿主（纯领域库场景） 不会加载它，也不会因缺
 * spring-web 而启动失败。
 *
 * <p><b>鉴权</b>：每个端点先过 {@link WorkflowDesignerAuthorizer}（动作档位见 {@link WorkflowDesignerAction}），拒绝即
 * 403。前端的路由 / 按钮守卫只是 UX，服务端这一层才是真边界；缺省实现是 <b>fail-closed 拒绝</b>，宿主不接授权就整体 403（见 {@link
 * WorkflowDesignerAuthorizer} 类注释）。
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

  /**
   * 流程定义原文的<b>字节</b>上限（1 MiB，UTF-8 编码后）。
   *
   * <p>为什么要卡：{@code definition_json} 落 JSON 列，正常业务定义在 10^4 字节量级；不设上限时一个超大 body 就能让 Jackson
   * 整棵树进内存，把服务打成 OOM。这是 OWASP「约束输入长度」的直接落地——在反序列化<b>之前</b>拒绝，而不是等 OOM 之后 再查 GC
   * 日志。上限留了两个数量级余量，不会误伤真实流程。
   *
   * <p><b>为什么按字节而不按 {@code String#length()}</b>：{@code length()} 数的是 UTF-16 code unit，与内存占用不是
   * 同一口径——一个汉字算 1、3 字节；照字符数卡 1 Mi，实际放进来的是 3 MiB 的 body，防护效果打了三折。内存与网络都是字节口径，
   * 限制器必须与它要保护的东西同口径，否则「上限」只是看着像有。
   */
  private static final int MAX_DEFINITION_BYTES = 1 << 20;

  private final WorkflowDefinitionCommandService command;
  private final WorkflowSimulationService simulation;
  private final WorkflowDesignerAuthorizer authorizer;

  /**
   * 生产装配构造。<b>必须显式 {@code @Autowired}</b>：本类另有一个包级「仅供测试」的两参构造，两个构造都非无参 ⇒ Spring 的构造推断（{@code
   * AutowiredAnnotationBeanPostProcessor#determineCandidateConstructors}）在无 {@code @Autowired}
   * 标注时<b>无法裁决</b>，会退化成「找无参构造」并抛 {@code No default constructor found} ——2026-10-27 联调实测：{@code
   * bone-boot} 组合根（{@code MetadataApplication} 扫 {@code com.bone.metadata} 命中 engine starter 的
   * {@code @SpringBootApplication(scanBasePackages="com.bone")} 兜底全扫）把本类当普通 bean
   * 扫进来，整个应用起不来。显式标注后，无论被扫到还是被自动配置的 {@code @Bean} 注册，都锁定三参生产构造。
   */
  @Autowired
  public WorkflowDesignerController(
      WorkflowDefinitionCommandService command,
      WorkflowSimulationService simulation,
      WorkflowDesignerAuthorizer authorizer) {
    this.command = command;
    this.simulation = simulation;
    this.authorizer = authorizer;
  }

  /** 无鉴权装配的便捷构造（<b>仅供测试</b>）：语义上等价于显式传入 allowAll，生产装配必须走三参构造。 */
  WorkflowDesignerController(
      WorkflowDefinitionCommandService command, WorkflowSimulationService simulation) {
    this(command, simulation, WorkflowDesignerAuthorizer.allowAll());
  }

  /** 只校验不落库：给设计器边改边看。请求体即流程定义原文（与保存/发布同口径）。 */
  @PostMapping("/validate")
  public ApiResponse<WorkflowDefinitionCommandService.ValidationReport> validate(
      @RequestBody String definitionJson) {
    authorizer.authorize(WorkflowDesignerAction.READ, null, null);
    return ApiResponse.ok(command.validate(requireDefinition(definitionJson, "校验")));
  }

  /**
   * 定义总览。
   *
   * @param includeOffline {@code true} 时把已下线的 key 一并返回（设计器用：否则下线后该 key 从列表消失，
   *     再没有任何入口把它重新上线）。运行期消费方用缺省的 {@code false}（只取当前生效定义）。
   */
  @GetMapping
  public ApiResponse<List<WorkflowDefinitionCommandService.Summary>> listLatest(
      @RequestParam(name = "includeOffline", defaultValue = "false") boolean includeOffline) {
    authorizer.authorize(WorkflowDesignerAction.READ, null, null);
    return ApiResponse.ok(command.listLatest(includeOffline));
  }

  /** 取草稿（无草稿返回 {@code data=null}，前端据此判定「新建」）。 */
  @GetMapping("/{key}/draft")
  public ApiResponse<WorkflowDefinitionCommandService.Draft> findDraft(@PathVariable String key) {
    authorizer.authorize(WorkflowDesignerAction.READ, key, null);
    return ApiResponse.ok(command.findDraft(key).orElse(null));
  }

  /** 保存草稿（新建或覆盖，覆盖需 {@code expectedRevision}）。 */
  @PostMapping("/{key}/draft")
  public ApiResponse<WorkflowDefinitionCommandService.SaveDraftResult> saveDraft(
      @PathVariable String key,
      @RequestHeader("X-Operator-Id") String operatorId,
      @RequestBody SaveDraftRequest request) {
    authorizer.authorize(WorkflowDesignerAction.WRITE, key, operatorId);
    return ApiResponse.ok(
        command.saveDraft(
            key,
            requireDefinition(request.definitionJson(), "保存草稿"),
            request.expectedRevision(),
            operatorId));
  }

  /** 丢弃草稿（物理删除）。 */
  @DeleteMapping("/{key}/draft")
  public ApiResponse<Void> discardDraft(
      @PathVariable String key, @RequestHeader("X-Operator-Id") String operatorId) {
    authorizer.authorize(WorkflowDesignerAction.WRITE, key, operatorId);
    command.discardDraft(key);
    return ApiResponse.ok(null);
  }

  /** 发布草稿为不可变版本（fail-closed：有违规 422）。 */
  @PostMapping("/{key}/publish")
  public ApiResponse<WorkflowDefinitionCommandService.Published> publish(
      @PathVariable String key,
      @RequestHeader("X-Operator-Id") String operatorId,
      @RequestBody PublishRequest request) {
    authorizer.authorize(WorkflowDesignerAction.PUBLISH, key, operatorId);
    return ApiResponse.ok(command.publish(key, request.expectedRevision(), operatorId));
  }

  /** 上线 / 下线某版本。 */
  @PostMapping("/{key}/enabled")
  public ApiResponse<WorkflowDefinitionCommandService.Published> setEnabled(
      @PathVariable String key,
      @RequestHeader("X-Operator-Id") String operatorId,
      @RequestBody EnabledRequest request) {
    authorizer.authorize(WorkflowDesignerAction.ENABLE, key, operatorId);
    return ApiResponse.ok(
        command.setEnabled(key, request.version(), request.enabled(), operatorId));
  }

  /** 某 key 的全部版本（含已下线）。 */
  @GetMapping("/{key}/versions")
  public ApiResponse<List<WorkflowDefinitionCommandService.Published>> listVersions(
      @PathVariable String key) {
    authorizer.authorize(WorkflowDesignerAction.READ, key, null);
    return ApiResponse.ok(command.listVersions(key));
  }

  /** 指定版本（含 {@code ui} 段原文，供设计器还原版式）；不存在返回 404。 */
  @GetMapping("/{key}/versions/{version}")
  public ResponseEntity<ApiResponse<WorkflowDefinitionCommandService.Published>> findVersion(
      @PathVariable String key, @PathVariable int version) {
    authorizer.authorize(WorkflowDesignerAction.READ, key, null);
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
    authorizer.authorize(WorkflowDesignerAction.READ, null, null);
    String definitionJson = requireDefinition(request.definitionJson(), "试运行");
    return ApiResponse.ok(
        simulation.simulate(
            definitionJson,
            request.sampleData(),
            request.initiatorId(),
            request.entityCode(),
            request.recordId()));
  }

  /**
   * 定义原文的形态与长度前置校验。
   *
   * <p>放在控制器而不是命令服务：这是<b>传输层约束</b>（body 能有多大），宿主直接调用命令服务时不该背这个限制；且必须在反序列化前拒绝， 才有 OOM 防护意义。
   */
  private static String requireDefinition(String definitionJson, String scene) {
    if (definitionJson == null || definitionJson.isBlank()) {
      throw new WorkflowDefinitionRequestException(scene + "失败：流程定义原文为空");
    }
    if (exceedsByteLimit(definitionJson)) {
      throw new WorkflowDefinitionRequestException(
          scene
              + "失败：流程定义原文约 "
              + definitionJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
              + " 字节，超过上限 "
              + MAX_DEFINITION_BYTES
              + " 字节");
    }
    return definitionJson;
  }

  /**
   * 是否超过字节上限。
   *
   * <p><b>两级判断，避免为每个正常请求都编码一次</b>：{@code getBytes} 会复制一份等长数组，而这段代码在每个写请求都要跑——正常定义只有 几
   * KB，为了卡住罕见的大包而给 100% 的请求多分配 1 MiB 临时内存，是不划算的。
   *
   * <ul>
   *   <li>{@code chars > 上限} ⇒ 必然超：UTF-8 每字符至少 1 字节；
   *   <li>{@code chars * 3 <= 上限} ⇒ 必然不超：一个 UTF-16 code unit 最多编成 3 字节（BMP 上界；代理对是 2 个 unit 编成 4
   *       字节，更省）；
   *   <li>只有落在中间灰区才真正编码测量。
   * </ul>
   */
  private static boolean exceedsByteLimit(String definitionJson) {
    int chars = definitionJson.length();
    if (chars > MAX_DEFINITION_BYTES) {
      return true;
    }
    if (chars * 3L <= MAX_DEFINITION_BYTES) {
      return false;
    }
    return definitionJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
        > MAX_DEFINITION_BYTES;
  }
}
