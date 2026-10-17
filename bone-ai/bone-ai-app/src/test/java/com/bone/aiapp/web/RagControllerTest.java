package com.bone.aiapp.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.aiapp.config.BoneAiProperties;
import com.bone.airag.application.RagService;
import com.bone.airag.domain.model.RagResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RagControllerTest {

  private final RagService ragService = mock(RagService.class);
  private final BoneAiProperties props = mock(BoneAiProperties.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new RagController(ragService, props)).build();
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
}
