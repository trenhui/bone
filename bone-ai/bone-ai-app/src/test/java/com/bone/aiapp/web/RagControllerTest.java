package com.bone.aiapp.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.aiapp.config.AiTenantContext;
import com.bone.aiapp.config.BoneAiProperties;
import com.bone.airag.application.RagApplicationService;
import com.bone.airag.domain.model.RagResult;
import com.bone.core.tenant.context.TenantContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RagControllerTest {

  private final RagApplicationService ragService = mock(RagApplicationService.class);
  private final BoneAiProperties props = mock(BoneAiProperties.class);
  private final AiTenantContext tenantContext = new AiTenantContext();
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new RagController(ragService, props, tenantContext))
            .build();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void retrieve_delegates_and_wraps() throws Exception {
    when(ragService.retrieve(any()))
        .thenReturn(List.of(new RagResult("c1", "clause-1", 0, 10, 0.9, "ch1", "text")));
    mockMvc
        .perform(
            post("/api/ai/v1/rag/retrieve")
                .contentType("application/json")
                .content("{\"query\":\"q\",\"docScope\":\"doc-1\",\"topK\":3}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].clauseRef").value("clause-1"));
  }

  @Test
  void ingest_uses_tenant_from_context() throws Exception {
    TenantContext.setTenantId("1");
    mockMvc
        .perform(
            post("/api/ai/v1/rag/ingest")
                .contentType("application/json")
                .content(
                    "{\"docId\":\"doc-1\",\"segments\":[{\"sectionPath\":\"§1\",\"text\":\"免赔额\","
                        + "\"clauseRef\":\"clause-1\",\"spanStart\":0,\"spanEnd\":10}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }
}
