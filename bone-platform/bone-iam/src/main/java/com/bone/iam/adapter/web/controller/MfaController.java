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

  // 2026-10-04 复核结论（P1-8 的**不**执行项）：本组端点**保持无方法级授权**。
  // 两条反对理由，都指向"改了要付的合约代价 > 收益"：
  //   ① 真实风险低于报告评估：SecurityConfig 是 anyRequest().authenticated()，
  //      端点在 URL 层已强制认证，"任何人可写"不成立；且方法级 @PreAuthorize 走
  //      MethodInterceptor，只会把「已登录但无码」的响应从 501 改成 403 ——
  //      而 501 是已写进 OpenAPI 契约的响应，前端按 501 提示"MFA 未启用"，
  //      改成 403 会变成"无权限"，属于一次可见的契约变更。
  //   ② 恒 501 期间不存在可授权资源，豁免理由（controller-authorization-baseline.json）
  //      依然成立；能力上线时应**同期**加码并同步 OpenAPI + 四层权限码真源。
  // 复核时把这条显式记下来，是为了避免下次有人看到"MFA 端点无 @PreAuthorize"
  // 就当成漏挂又重提一次 —— 它不是漏挂，是已登记的豁免。
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
