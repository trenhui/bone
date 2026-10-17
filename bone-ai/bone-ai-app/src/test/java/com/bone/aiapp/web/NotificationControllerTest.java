package com.bone.aiapp.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.aiapp.config.BoneAiProperties;
import com.bone.ainotification.application.AlertNotificationService;
import com.bone.ainotification.domain.model.NotificationChannel;
import com.bone.ainotification.domain.model.NotificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NotificationControllerTest {

  private final AlertNotificationService notificationService = mock(AlertNotificationService.class);
  private final BoneAiProperties props = mock(BoneAiProperties.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new NotificationController(notificationService, props))
            .build();
  }

  @Test
  void dispatch_delegates_and_wraps() throws Exception {
    when(notificationService.dispatch(any(), any(), any()))
        .thenReturn(new NotificationResult(NotificationChannel.WEBHOOK, true, "ref-1", null));
    mockMvc
        .perform(
            post("/api/ai/v1/notify/dispatch")
                .contentType("application/json")
                .content(
                    "{\"alertId\":\"a1\",\"tenantId\":1,\"severity\":\"HIGH\",\"title\":\"t\","
                        + "\"body\":\"b\",\"sourceModule\":\"m\",\"channel\":\"WEBHOOK\","
                        + "\"target\":\"https://hook\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.delivered").value(true));
  }

  @Test
  void ack_delegates_to_sink() throws Exception {
    mockMvc
        .perform(
            post("/api/ai/v1/notify/ack")
                .contentType("application/json")
                .content("{\"alertId\":\"a1\",\"tenantId\":1,\"ackedBy\":\"u1\",\"note\":\"ok\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
    verify(notificationService).acknowledge(any());
  }
}
