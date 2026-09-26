package com.bone.studio.generator.adapter.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bone.core.common.CommonErrorCodes;
import com.bone.core.exception.BizException;
import com.bone.studio.generator.infrastructure.config.GeneratorExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 错误响应的 {@code ProblemDetail} 契约测试（i18n 方案 §6.2.4，照抄 blueprint §12.3 #3）。
 *
 * <p>锁定：generator 的 {@link GeneratorExceptionHandler} 在任何非 2xx 响应里都产出带 {@code errorCode} 的
 * ProblemDetail —— 业务异常透传自带码，兜底异常归入 {@code COMMON_INTERNAL_ERROR}，二者 {@code data} 都不为 null。
 */
class ProblemDetailContractTest {

  @RestController
  static class ThrowingController {
    @GetMapping("/_contract/not-found")
    void notFound() {
      throw new BizException(404, "资源不存在", CommonErrorCodes.NOT_FOUND);
    }

    @GetMapping("/_contract/boom")
    void boom() {
      throw new RuntimeException("未预期");
    }
  }

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GeneratorExceptionHandler())
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

  /** 兜底异常也产出带 {@code COMMON_INTERNAL_ERROR} 的 ProblemDetail，而非裸 null。 */
  @Test
  void fallbackCarriesErrorCode() throws Exception {
    mockMvc
        .perform(get("/_contract/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.data.errorCode").value(CommonErrorCodes.INTERNAL_ERROR));
  }
}
