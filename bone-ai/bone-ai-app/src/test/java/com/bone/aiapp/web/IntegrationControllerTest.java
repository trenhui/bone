package com.bone.aiapp.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.aiapp.config.AiTenantContext;
import com.bone.aiapp.config.BoneAiProperties;
import com.bone.aiintegration.application.IntegrationApplicationService;
import com.bone.aiintegration.domain.model.ExternalCallResult;
import com.bone.aiintegration.domain.model.IntegrationMode;
import com.bone.core.tenant.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IntegrationControllerTest {

  private final IntegrationApplicationService integrationService =
      mock(IntegrationApplicationService.class);
  private final BoneAiProperties props = mock(BoneAiProperties.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new IntegrationController(integrationService, new AiTenantContext(), props))
            .build();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void invoke_delegates_to_port_and_wraps() throws Exception {
    TenantContext.setTenantId("1");
    when(integrationService.invoke(any()))
        .thenReturn(
            new ExternalCallResult(
                "c1", "SYS", 200, "{}", IntegrationMode.AUTOMATED, true, false, 5L, null));
    mockMvc
        .perform(
            post("/api/ai/v1/integration/invoke")
                .contentType("application/json")
                .content(
                    "{\"callId\":\"c1\",\"systemId\":\"SYS\",\"endpoint\":\"https://x\","
                        + "\"payload\":\"{}\",\"mode\":\"AUTOMATED\",\"correlationId\":\"r1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.used").value(true));
  }
}
