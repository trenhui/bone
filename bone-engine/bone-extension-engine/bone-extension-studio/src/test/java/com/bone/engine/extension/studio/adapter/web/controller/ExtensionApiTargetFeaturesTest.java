package com.bone.engine.extension.studio.adapter.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.engine.extension.studio.ExtensionStudioApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = ExtensionStudioApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("in-memory")
class ExtensionApiTargetFeaturesTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("POST /points 返回 201 与 Location")
  void createPoint_returns201AndLocation() throws Exception {
    // 种子数据占用 id 1、2，新建为 3
    mockMvc
        .perform(
            post("/api/v1/extension/points")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                        {
                                          "name": "契约扩展点",
                                          "interfaceName": "com.bone.test.ContractExtPoint",
                                          "domain": "test",
                                          "enabled": true
                                        }
                                        """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/extension/points/3"))
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  @DisplayName("PUT /points/{id} 带错误 If-Match 返回 412")
  void updatePoint_wrongIfMatch_returns412() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/extension/points/1")
                .header("If-Match", "\"v999\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                        {"name":"改名的订单计价","interfaceName":"com.bone.example.extension.order.OrderPricingExtPoint","enabled":true}
                                        """))
        .andExpect(status().isPreconditionFailed())
        .andExpect(jsonPath("$.data.errorCode").value("COMMON_PRECONDITION_FAILED"));
  }

  @Test
  @DisplayName("Idempotency-Key 重复请求返回相同 201")
  void createPoint_idempotentReplay() throws Exception {
    // 单独接口名，避免与其它用例争用 id
    String body =
        """
                {"name":"幂等扩展点","interfaceName":"com.bone.test.IdempotentReplayOnly","domain":"test","enabled":true}
                """;
    String key = "11111111-1111-1111-1111-111111111111";
    var first =
        mockMvc
            .perform(
                post("/api/v1/extension/points")
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    String location = first.getResponse().getHeader("Location");

    mockMvc
        .perform(
            post("/api/v1/extension/points")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", location));
  }
}
