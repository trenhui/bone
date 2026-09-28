package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.iam.adapter.web.dto.response.MfaStatusResp;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.common.IamErrors;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MFA 接口（社区版：状态查询 As-Is；注册/验证 **[Vision]** 返回 501）。
 *
 * <p><b>能力未上线</b>：{@code /enroll}、{@code /verify} 显式返回 501，MFA 仅在商业版 / IdP 中启用。
 * 前端不应暴露注册/验证入口，避免误调用。
 */
@Deprecated(since = "vision", forRemoval = false)
@RestController
@RequestMapping(PlatformApiPaths.IAM_V1 + "/mfa")
public class MfaController {

  @GetMapping("/status")
  public ApiResponse<MfaStatusResp> status() {
    MfaStatusResp resp = new MfaStatusResp();
    resp.setEnabled(false);
    resp.setEnrolled(false);
    resp.setMethods(List.of());
    return ApiResponse.success(resp);
  }

  @PostMapping("/enroll")
  public ResponseEntity<ApiResponse<Void>> enroll(@RequestBody Map<String, Object> body) {
    // B-12：能力未上线统一走业务码，由 IamExceptionHandler 渲染 HTTP 501 + errorCode=IAM_MFA_NOT_AVAILABLE，
    // 前端 / 监控可据此聚合（不再是无码裸消息）。
    throw IamErrors.of(IamErrorCodes.MFA_NOT_AVAILABLE, "MFA 未在商业版/IdP 中启用");
  }

  @PostMapping("/verify")
  public ResponseEntity<ApiResponse<Void>> verify(@RequestBody Map<String, Object> body) {
    throw IamErrors.of(IamErrorCodes.MFA_NOT_AVAILABLE, "MFA 未在商业版/IdP 中启用");
  }
}
