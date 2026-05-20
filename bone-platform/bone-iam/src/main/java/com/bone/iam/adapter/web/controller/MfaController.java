package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import com.bone.iam.adapter.web.dto.resp.MfaStatusResp;
import com.bone.iam.common.IamErrorCodes;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MFA 接口（社区版：状态查询 As-Is；注册/验证 **[Vision]** 返回 501）。
 */
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
        return notAvailable();
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Void>> verify(@RequestBody Map<String, Object> body) {
        return notAvailable();
    }

    private static ResponseEntity<ApiResponse<Void>> notAvailable() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponse.error(501, IamErrorCodes.MFA_NOT_AVAILABLE + ": MFA 未在商业版/IdP 中启用"));
    }
}
