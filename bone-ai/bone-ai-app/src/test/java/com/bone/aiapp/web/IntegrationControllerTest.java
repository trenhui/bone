package com.bone.aiapp.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.aiapp.config.BoneAiProperties;
import com.bone.aiintegration.application.IntegrationService;
import com.bone.aiintegration.domain.model.ExternalCallResult;
import com.bone.aiintegration.domain.model.IntegrationMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IntegrationControllerTest {

  private final IntegrationService integrationService = mock(IntegrationService.class);
  private final BoneAiProperties props = mock(BoneAiProperties.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new IntegrationController(integrationService, props))
            .build();
  }

  @Test
  void invoke_delegates_to_port_and_wraps() throws Exception {
    when(integrationService.invoke(any()))
        .thenReturn(
            new ExternalCallResult(
                "c1", "SYS", 200, "{}", IntegrationMode.AUTOMATED, true, false, 5L, null));
    mockMvc
        .perform(
            post("/api/ai/v1/integration/invoke")
                .contentType("application/json")
                .content(
                    "{\"callId\":\"c1\",\"tenantId\":1,\"systemId\":\"SYS\",\"endpoint\":\"https://x\","
                        + "\"payload\":\"{}\",\"mode\":\"AUTOMATED\",\"correlationId\":\"r1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.used").value(true));
  }
}
