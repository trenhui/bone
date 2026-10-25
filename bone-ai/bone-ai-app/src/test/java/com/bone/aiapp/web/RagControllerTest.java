package com.bone.aiapp.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.aiapp.config.AiTenantContext;
import com.bone.aiapp.config.BoneAiProperties;
import com.bone.airag.application.RagAnswerOutcome;
import com.bone.airag.application.RagAnswerService;
import com.bone.airag.application.RagApplicationService;
import com.bone.airag.application.RagApplicationService.RagRetrieveOutcome;
import com.bone.airag.domain.model.GroundingVerdict;
import com.bone.airag.domain.model.RagQuery;
import com.bone.airag.domain.model.RagResult;
import com.bone.core.tenant.context.TenantContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RagControllerTest {

  private final RagApplicationService ragService = mock(RagApplicationService.class);
  private final RagAnswerService ragAnswerService = mock(RagAnswerService.class);
  private final BoneAiProperties props = mock(BoneAiProperties.class);
  private final AiTenantContext tenantContext = new AiTenantContext();
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new RagController(ragService, ragAnswerService, props, tenantContext))
            .build();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void retrieve_delegates_and_wraps() throws Exception {
    when(ragService.retrieve(any()))
        .thenReturn(
            new RagRetrieveOutcome(
                List.of(new RagResult("c1", "clause-1", 0, 10, 0.9, "ch1", "text")), List.of()));
    mockMvc
        .perform(
            post("/api/ai/v1/rag/retrieve")
                .contentType("application/json")
                .content("{\"query\":\"q\",\"docScope\":\"doc-1\",\"topK\":3}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.results[0].clauseRef").value("clause-1"))
        .andExpect(jsonPath("$.data.degradations").isEmpty());
  }

  @Test
  void retrieve_applies_score_and_scan_guards_from_request() throws Exception {
    when(ragService.retrieve(any())).thenReturn(new RagRetrieveOutcome(List.of(), List.of()));
    when(props.getRagTopK()).thenReturn(3);
    when(props.getRagMinScore()).thenReturn(0.1);
    when(props.getRagMaxScan()).thenReturn(500);

    // 请求级 minScore / maxScan 覆盖配置默认值（详设 §2.6 护栏）
    mockMvc
        .perform(
            post("/api/ai/v1/rag/retrieve")
                .contentType("application/json")
                .content("{\"query\":\"q\",\"topK\":3,\"minScore\":0.5,\"maxScan\":100}"))
        .andExpect(status().isOk());

    ArgumentCaptor<RagQuery> captor = ArgumentCaptor.forClass(RagQuery.class);
    verify(ragService).retrieve(captor.capture());
    assertThat(captor.getValue().minScore()).isEqualTo(0.5);
    assertThat(captor.getValue().maxScan()).isEqualTo(100);
    assertThat(captor.getValue().topK()).isEqualTo(3);
  }

  @Test
  void retrieve_surfaces_degradations() throws Exception {
    // provider 故障降级时，痕迹码必须透出到响应（接入方 / 评测管线可观测，而非只翻服务端日志）
    when(ragService.retrieve(any()))
        .thenReturn(
            new RagRetrieveOutcome(List.of(), List.of(RagApplicationService.DEGRADE_EMBEDDING)));

    mockMvc
        .perform(
            post("/api/ai/v1/rag/retrieve")
                .contentType("application/json")
                .content("{\"query\":\"q\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.degradations[0]").value("EMBEDDING_DEGRADED"));
  }

  @Test
  void retrieve_passes_query_through() throws Exception {
    when(ragService.retrieve(any())).thenReturn(new RagRetrieveOutcome(List.of(), List.of()));
    when(props.getRagTopK()).thenReturn(3);

    // MMR λ 为模块级旋钮（bone.ai.rag.mmr-lambda），不走请求级——请求只带 query/护栏
    mockMvc
        .perform(
            post("/api/ai/v1/rag/retrieve")
                .contentType("application/json")
                .content("{\"query\":\"q\"}"))
        .andExpect(status().isOk());

    ArgumentCaptor<RagQuery> captor = ArgumentCaptor.forClass(RagQuery.class);
    verify(ragService).retrieve(captor.capture());
    assertThat(captor.getValue().query()).isEqualTo("q");
  }

  @Test
  void ingest_passes_doc_version_batch_key() throws Exception {
    TenantContext.setTenantId("1");
    mockMvc
        .perform(
            post("/api/ai/v1/rag/ingest")
                .contentType("application/json")
                .content(
                    "{\"docId\":\"doc-1\",\"docVersion\":\"v2\",\"segments\":[{\"sectionPath\":\"§1\","
                        + "\"text\":\"免赔额\",\"clauseRef\":\"clause-1\",\"spanStart\":0,\"spanEnd\":10}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    // doc_version 为增量幂等批键，必须透传到 ingest（详设 §2.6）
    ArgumentCaptor<String> verCaptor = ArgumentCaptor.forClass(String.class);
    verify(ragService).ingest(eq(1L), eq("doc-1"), verCaptor.capture(), anyList());
    assertThat(verCaptor.getValue()).isEqualTo("v2");
  }

  @Test
  void ingest_uses_tenant_from_context() throws Exception {
    TenantContext.setTenantId("1");
    mockMvc
        .perform(
            post("/api/ai/v1/rag/ingest")
                .contentType("application/json")
                .content(
                    "{\"docId\":\"doc-1\",\"docVersion\":\"v1\",\"segments\":[{\"sectionPath\":\"§1\",\"text\":\"免赔额\","
                        + "\"clauseRef\":\"clause-1\",\"spanStart\":0,\"spanEnd\":10}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void answer_delegates_to_answer_service_and_wraps() throws Exception {
    // R1 接 G：回答链闭环透传（接地答案 + 出处上下文）
    when(ragAnswerService.answer(any()))
        .thenReturn(
            new RagAnswerOutcome(
                true,
                "逾期付款按日千分之一[1]。",
                null,
                new GroundingVerdict(true, "PASS", 1, 0, 1.0, List.of(), List.of()),
                List.of(new RagResult("c1", "clause-1", 0, 10, 0.9, "ch1", "text")),
                List.of(),
                10,
                false,
                null));

    mockMvc
        .perform(
            post("/api/ai/v1/rag/answer")
                .contentType("application/json")
                .content("{\"query\":\"q\",\"docScope\":\"doc-1\",\"topK\":3}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.answered").value(true))
        .andExpect(jsonPath("$.data.answer").value("逾期付款按日千分之一[1]。"))
        .andExpect(jsonPath("$.data.contexts[0].clauseRef").value("clause-1"));
  }

  @Test
  void answer_surfaces_refusal_with_error_code() throws Exception {
    // 拒答是可控业务结果：answered=false + errorCode（治理拒绝保真透传）+ 统一拒答文案，不抛异常
    when(ragAnswerService.answer(any()))
        .thenReturn(RagAnswerOutcome.unavailable("AI_GATEWAY_INJECTION_SUSPECTED", "疑似提示注入"));

    mockMvc
        .perform(
            post("/api/ai/v1/rag/answer")
                .contentType("application/json")
                .content("{\"query\":\"q\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.answered").value(false))
        .andExpect(jsonPath("$.data.errorCode").value("AI_GATEWAY_INJECTION_SUSPECTED"))
        .andExpect(jsonPath("$.data.answer").value(RagAnswerService.REFUSAL_ANSWER));
  }

  @Test
  void answer_applies_score_and_scan_guards_from_request() throws Exception {
    // 回答链与检索同口径：请求级 minScore / maxScan 覆盖配置默认值（详设 §2.6 护栏）
    when(ragAnswerService.answer(any()))
        .thenReturn(
            new RagAnswerOutcome(
                false,
                RagAnswerService.REFUSAL_ANSWER,
                "AI_RAG_GROUNDING_FAILED",
                new GroundingVerdict(false, "NO_CONTEXT", 0, 0, 0.0, List.of(), List.of()),
                List.of(),
                List.of(),
                0,
                false,
                null));
    when(props.getRagTopK()).thenReturn(3);
    when(props.getRagMinScore()).thenReturn(0.1);
    when(props.getRagMaxScan()).thenReturn(500);

    mockMvc
        .perform(
            post("/api/ai/v1/rag/answer")
                .contentType("application/json")
                .content("{\"query\":\"q\",\"topK\":3,\"minScore\":0.5,\"maxScan\":100}"))
        .andExpect(status().isOk());

    ArgumentCaptor<RagQuery> captor = ArgumentCaptor.forClass(RagQuery.class);
    verify(ragAnswerService).answer(captor.capture());
    assertThat(captor.getValue().minScore()).isEqualTo(0.5);
    assertThat(captor.getValue().maxScan()).isEqualTo(100);
    assertThat(captor.getValue().topK()).isEqualTo(3);
  }
}
