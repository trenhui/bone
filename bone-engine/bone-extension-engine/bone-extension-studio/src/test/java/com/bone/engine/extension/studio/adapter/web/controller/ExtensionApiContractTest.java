package com.bone.engine.extension.studio.adapter.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** Provider 契约冒烟（Bone-API-规范 §15.3）：信封字段、ProblemDetail、游标分页头。 */
@SpringBootTest(classes = ExtensionStudioApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("in-memory")
class ExtensionApiContractTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("成功响应符合 ApiResponse 信封")
  void successEnvelope() throws Exception {
    mockMvc
        .perform(get("/api/v1/extension/overview"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.message").isString())
        .andExpect(jsonPath("$.data").exists())
        .andExpect(jsonPath("$.timestamp").isString())
        .andExpect(header().exists("X-Trace-Id"));
  }

  @Test
  @DisplayName("404 返回 ProblemDetail（含 errorCode、traceId、统一 type/title）")
  void notFound_returnsProblemDetail() throws Exception {
    mockMvc
        .perform(get("/api/v1/extension/points/999999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.data.errorCode").value("EXT_RESOURCE_NOT_FOUND"))
        .andExpect(jsonPath("$.data.traceId").isString())
        .andExpect(jsonPath("$.data.detail").isString())
        // 形状必须与全平台一致（ProblemDetails 工厂 + Bone-API-规范 ProblemDetail.yaml）：
        // type 为 https://bone.wps.cn/problems/<errorCode 小写连字符>，title 为 HTTP 状态文本。
        // 改造前本模块用遗留 ProblemDetail.of：type 恒 about:blank、title 直接放 errorCode——
        // 这两条断言即锁定该不一致不得回归（改成遗留构造即红）。
        .andExpect(
            jsonPath("$.data.type").value("https://bone.wps.cn/problems/ext-resource-not-found"))
        .andExpect(jsonPath("$.data.title").value("Not Found"));
  }

  @Test
  @DisplayName("执行日志默认游标分页含 records")
  void executionLogs_cursorPageResult() throws Exception {
    mockMvc
        .perform(get("/api/v1/extension/execution-logs").param("limit", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.records").isArray());
  }

  @Test
  @DisplayName("冒号动作 deploy 返回 200 信封")
  void deployAction_colonSuffix() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/extension/plugins/1:deploy")
                .param("sync", "true")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(1));
  }
}
