package com.bone.workflow.starter.designer.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.domain.WorkflowDefinitionValidator;
import com.bone.workflow.domain.WorkflowSimulator;
import com.bone.workflow.ports.spi.ApproverResolver;
import com.bone.workflow.starter.designer.WorkflowDefinitionCommandService;
import com.bone.workflow.starter.designer.WorkflowDesignerAccessDeniedException;
import com.bone.workflow.starter.designer.WorkflowDesignerAction;
import com.bone.workflow.starter.designer.WorkflowDesignerAuthorizer;
import com.bone.workflow.starter.designer.WorkflowSimulationService;
import com.bone.workflow.starter.sdk.WfProcessDefSdkRepository;
import com.bone.workflow.starter.sdk.WfProcessDraftSdkRepository;
import com.bone.workflow.starter.sdk.WorkflowSdkTestContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 设计器 REST 层（v1.5）契约测试：standalone MockMvc 直接驱动真实命令/模拟服务，验证 HTTP 状态码、响应信封 与异常映射——不依赖宿主 Servlet
 * 容器，也不触发 web 自动配置的条件分支。
 *
 * <p>覆盖的契约（与 {@link WorkflowDesignerExceptionAdvice} 映射口径一致）：
 *
 * <ul>
 *   <li>写路径审计列必须回答「谁改的」→ 缺 {@code X-Operator-Id} 头直接 400；
 *   <li>发布 fail-closed → 草稿有违规返回 422，且 {@code data.violations} 带结构化清单；
 *   <li>revision 乐观锁 → 拿旧 revision 覆盖 409；
 *   <li>版本缺失 → 404；校验只校验不落库（HTTP 始终 200，{@code valid} 字段表达结果）。
 * </ul>
 */
class WorkflowDesignerControllerTest {

  private static final long TENANT = 1L;
  private static final String DEF_KEY = "expense_designer";

  private AnnotationConfigApplicationContext context;
  private MockMvc mockMvc;
  private WorkflowDefinitionCommandService command;
  private WorkflowSimulationService simulation;

  private static final String VALID_JSON =
      "{\"key\":\"expense_designer\",\"name\":\"费用报销（设计器）\",\"version\":1,\"engineSchemaVersion\":1,"
          + "\"nodes\":[{\"code\":\"start\",\"type\":\"START\",\"label\":\"发起\"},"
          + "{\"code\":\"mgr\",\"type\":\"APPROVE\",\"mode\":\"SINGLE\",\"label\":\"直属主管\"},"
          + "{\"code\":\"end\",\"type\":\"END\",\"label\":\"结束\"}],"
          + "\"edges\":[{\"from\":\"start\",\"to\":\"mgr\"},{\"from\":\"mgr\",\"to\":\"end\"}],"
          + "\"assignments\":{\"mgr\":[{\"strategy\":\"LINE_MANAGER\",\"params\":{}}]},"
          + "\"ui\":{\"layout\":{\"start\":{\"x\":0,\"y\":0},\"mgr\":{\"x\":0,\"y\":120}}}}";

  private static final String DIRTY_JSON =
      "{\"key\":\"expense_designer\",\"name\":\"费用报销（半成品）\",\"version\":1,"
          + "\"nodes\":[{\"code\":\"start\",\"type\":\"START\",\"label\":\"发起\"},"
          + "{\"code\":\"mgr\",\"type\":\"APPROVE\",\"mode\":\"SINGLE\",\"label\":\"主管\"}],"
          + "\"edges\":[{\"from\":\"start\",\"to\":\"mgr\"}],"
          + "\"assignments\":{\"mgr\":[{\"strategy\":\"ROLE\",\"params\":{}}]}}";

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(TENANT);
    context = WorkflowSdkTestContext.create();
    ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
    AtomicInteger sequence = new AtomicInteger();

    WorkflowDefinitionCommandService command =
        new WorkflowDefinitionCommandService(
            context.getBean(WfProcessDraftSdkRepository.class),
            context.getBean(WfProcessDefSdkRepository.class),
            new WorkflowDefinitionValidator(),
            () -> "WF-ID-" + sequence.incrementAndGet(),
            objectMapper);

    ApproverResolver noopResolver = (rule, node, ctx) -> List.of();
    WorkflowSimulationService simulation =
        new WorkflowSimulationService(
            WorkflowSimulator.withDefaults(),
            new WorkflowDefinitionValidator(),
            noopResolver,
            objectMapper);

    this.command = command;
    this.simulation = simulation;
    mockMvc = buildMockMvc(WorkflowDesignerAuthorizer.allowAll());
  }

  /** 用指定授权实现装配一套 MockMvc（缺省 suite 用放行实现，鉴权用例另建一套）。 */
  private MockMvc buildMockMvc(WorkflowDesignerAuthorizer authorizer) {
    return MockMvcBuilders.standaloneSetup(
            new WorkflowDesignerController(command, simulation, authorizer))
        .setControllerAdvice(new WorkflowDesignerExceptionAdvice())
        .build();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    if (context != null) {
      context.close();
    }
  }

  @Test
  @DisplayName("validate 只校验不落库：合法定义返回 200 + valid=true")
  void validate_validDefinition_returns200AndValidTrue() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/workflow/definitions/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.valid").value(true))
        .andExpect(jsonPath("$.data.violations").isEmpty());
  }

  @Test
  @DisplayName("validate 坏 JSON：HTTP 仍 200，valid=false 且含 WF-021（边改边看，不抛 4xx）")
  void validate_brokenJson_returns200WithViolation() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/workflow/definitions/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not-json"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.valid").value(false))
        .andExpect(jsonPath("$.data.violations[0].code").value("WF-021"));
  }

  @Test
  @DisplayName("保存草稿：缺 X-Operator-Id 头 → 400（写路径审计列必须回答谁改的）")
  void saveDraft_missingOperatorHeader_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/workflow/definitions/{key}/draft", DEF_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"definitionJson\":" + quote(VALID_JSON) + "}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("保存草稿 → 200 + revision=1；随后覆盖带旧 revision → 409")
  void saveDraft_thenStaleRevisionConflict_returns409() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/workflow/definitions/{key}/draft", DEF_KEY)
                .header("X-Operator-Id", "u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(draftBody(VALID_JSON, null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.draft.revision").value(1));

    mockMvc
        .perform(
            post("/api/v1/workflow/definitions/{key}/draft", DEF_KEY)
                .header("X-Operator-Id", "u2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(draftBody(VALID_JSON, 1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.draft.revision").value(2));

    mockMvc
        .perform(
            post("/api/v1/workflow/definitions/{key}/draft", DEF_KEY)
                .header("X-Operator-Id", "u3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(draftBody(DIRTY_JSON, 1)))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("发布 fail-closed：草稿有违规 → 422 + data.violations 带结构化清单（不落版本行）")
  void publish_invalidDraft_returns422WithViolations() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/workflow/definitions/{key}/draft", DEF_KEY)
                .header("X-Operator-Id", "u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(draftBody(DIRTY_JSON, null)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/workflow/definitions/{key}/publish", DEF_KEY)
                .header("X-Operator-Id", "u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":1}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(422))
        .andExpect(jsonPath("$.data.violations").isArray())
        .andExpect(jsonPath("$.data.violations[0].code").exists());
  }

  @Test
  @DisplayName("发布成功 → 200 + version=1 + enabled=true；草稿清空（再取草稿 data=null）")
  void publish_validDraft_returns200AndClearsDraft() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/workflow/definitions/{key}/draft", DEF_KEY)
                .header("X-Operator-Id", "u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(draftBody(VALID_JSON, null)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/workflow/definitions/{key}/publish", DEF_KEY)
                .header("X-Operator-Id", "u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.version").value(1))
        .andExpect(jsonPath("$.data.enabled").value(true));

    mockMvc
        .perform(get("/api/v1/workflow/definitions/{key}/draft", DEF_KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  @Test
  @DisplayName("版本详情：存在 → 200；不存在 → 404")
  void findVersion_existing200_missing404() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/workflow/definitions/{key}/draft", DEF_KEY)
                .header("X-Operator-Id", "u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(draftBody(VALID_JSON, null)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/workflow/definitions/{key}/publish", DEF_KEY)
                .header("X-Operator-Id", "u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":1}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/workflow/definitions/{key}/versions/1", DEF_KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.version").value(1));

    mockMvc
        .perform(get("/api/v1/workflow/definitions/{key}/versions/99", DEF_KEY))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("定义总览只列当前生效版本；下线后从总览消失")
  void listLatest_reflectsEnabledState() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/workflow/definitions/{key}/draft", DEF_KEY)
                .header("X-Operator-Id", "u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(draftBody(VALID_JSON, null)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/workflow/definitions/{key}/publish", DEF_KEY)
                .header("X-Operator-Id", "u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":1}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/workflow/definitions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].version").value(1));

    mockMvc
        .perform(
            post("/api/v1/workflow/definitions/{key}/enabled", DEF_KEY)
                .header("X-Operator-Id", "u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1,\"enabled\":false}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/workflow/definitions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
  }

  @Test
  @DisplayName("试运行端点：合法定义返回 200 + 含 paths/warnings 字段")
  void simulate_validDefinition_returns200() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/workflow/definitions/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"definitionJson\":"
                        + quote(VALID_JSON)
                        + ",\"sampleData\":{},\"initiatorId\":\"u1\",\"entityCode\":null,\"recordId\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.path").isArray());
  }

  @Test
  @DisplayName("授权实现拒绝发布：403 + data.action=PUBLISH（与 400/422 分开，前端据此提示找管理员）")
  void publish_whenAuthorizerDenies_returns403() throws Exception {
    MockMvc denying =
        buildMockMvc(
            (action, defKey, operatorId) -> {
              if (action == WorkflowDesignerAction.PUBLISH) {
                throw new WorkflowDesignerAccessDeniedException(action, defKey, operatorId);
              }
            });

    denying
        .perform(
            post("/api/v1/workflow/definitions/{key}/draft", DEF_KEY)
                .header("X-Operator-Id", "u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(draftBody(VALID_JSON, null)))
        .andExpect(status().isOk());

    denying
        .perform(
            post("/api/v1/workflow/definitions/{key}/publish", DEF_KEY)
                .header("X-Operator-Id", "u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":1}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(403))
        .andExpect(jsonPath("$.data.action").value("PUBLISH"))
        .andExpect(jsonPath("$.data.defKey").value(DEF_KEY));
  }

  @Test
  @DisplayName("授权实现收到真实动作/目标 key/操作人：保存草稿对应 WRITE")
  void saveDraft_passesActionKeyAndOperatorToAuthorizer() throws Exception {
    java.util.concurrent.atomic.AtomicReference<String> seen =
        new java.util.concurrent.atomic.AtomicReference<>();
    MockMvc recording =
        buildMockMvc(
            (action, defKey, operatorId) -> seen.set(action + "|" + defKey + "|" + operatorId));

    recording
        .perform(
            post("/api/v1/workflow/definitions/{key}/draft", DEF_KEY)
                .header("X-Operator-Id", "u9")
                .contentType(MediaType.APPLICATION_JSON)
                .content(draftBody(VALID_JSON, null)))
        .andExpect(status().isOk());

    org.junit.jupiter.api.Assertions.assertEquals("WRITE|" + DEF_KEY + "|u9", seen.get());
  }

  @Test
  @DisplayName("流程定义原文超长：400（在反序列化前按字符数拒绝，防 OOM）")
  void validate_oversizedDefinition_returns400() throws Exception {
    String oversize = "{\"key\":\"" + DEF_KEY + "\",\"pad\":\"" + "x".repeat(1 << 20) + "\"}";

    mockMvc
        .perform(
            post("/api/v1/workflow/definitions/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(oversize))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400));
  }

  @Test
  @DisplayName("流程定义原文为空：400（而不是把空串塞进校验器）")
  void validate_blankDefinition_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/workflow/definitions/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("   "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400));
  }

  @Test
  @DisplayName("缺省授权实现 fail-closed：未接线时连读端点都 403，且文案给出接线办法")
  void defaultAuthorizer_whenNotWired_deniesEvenRead() throws Exception {
    MockMvc unwired = buildMockMvc(WorkflowDesignerAuthorizer.denyAll());

    unwired
        .perform(get("/api/v1/workflow/definitions"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(403))
        .andExpect(
            jsonPath("$.message").value(org.hamcrest.Matchers.containsString("allow-anonymous")));
  }

  private static String draftBody(String definitionJson, Integer expectedRevision) {
    if (expectedRevision == null) {
      return "{\"definitionJson\":" + quote(definitionJson) + ",\"expectedRevision\":null}";
    }
    return "{\"definitionJson\":"
        + quote(definitionJson)
        + ",\"expectedRevision\":"
        + expectedRevision
        + "}";
  }

  private static String quote(String raw) {
    return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
