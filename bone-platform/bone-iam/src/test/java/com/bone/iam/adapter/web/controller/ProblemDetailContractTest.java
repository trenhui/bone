package com.bone.iam.adapter.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.core.common.CommonErrorCodes;
import com.bone.core.exception.BizException;
import com.bone.iam.infrastructure.config.IamExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 错误响应的 {@code ProblemDetail} 契约测试（i18n 方案 §6.2.4，照抄 blueprint §12.3 #3）。
 *
 * <p>锁定：IAM 的 {@link IamExceptionHandler} 在任何非 2xx 响应里都产出带 {@code errorCode} 的 ProblemDetail ——
 * 否则前端 {@code showError()} 的 errorCode 分支不命中，英文用户只能看到中文 toast。
 */
class ProblemDetailContractTest {

  @RestController
  static class ThrowingController {
    @GetMapping("/_contract/not-found")
    void notFound() {
      throw new BizException(404, "资源不存在", CommonErrorCodes.NOT_FOUND);
    }

    @PostMapping("/_contract/body")
    void body(@Valid @RequestBody Req req) {}
  }

  record Req(@NotBlank String name) {}

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new IamExceptionHandler())
            .build();
  }

  /** 业务异常：码必须由异常自带并透传进 {@code data.errorCode}。 */
  @Test
  void bizExceptionCarriesErrorCode() throws Exception {
    mockMvc
        .perform(get("/_contract/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.data.errorCode").value(CommonErrorCodes.NOT_FOUND))
        .andExpect(jsonPath("$.data.status").value(404))
        .andExpect(jsonPath("$.message").exists());
  }

  /** 参数校验失败：码统一为 {@code COMMON_VALIDATION_FAILED}，并给出结构化字段错误。 */
  @Test
  void validationCarriesErrorCodeAndFieldErrors() throws Exception {
    mockMvc
        .perform(post("/_contract/body").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.data.errorCode").value(CommonErrorCodes.VALIDATION_FAILED))
        .andExpect(jsonPath("$.data.errors").isArray())
        .andExpect(jsonPath("$.data.errors[0].field").exists());
  }
}
