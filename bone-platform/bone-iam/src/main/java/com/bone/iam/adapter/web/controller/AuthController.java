package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.iam.application.command.cmd.LoginCmd;
import com.bone.iam.application.command.handler.LoginHandler;
import com.bone.iam.adapter.web.dto.req.LoginReq;
import com.bone.iam.adapter.web.dto.resp.LoginResp;
import com.bone.iam.adapter.web.converter.AuthWebConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/iam")
@RequiredArgsConstructor
public class AuthController {
    private final LoginHandler loginHandler;
    private final AuthWebConverter authWebConverter;

    @PostMapping("/login")
    public ApiResponse<LoginResp> login(@RequestBody LoginReq req) {
        LoginCmd cmd = authWebConverter.toLoginCmd(req);
        Map<String, Object> result = loginHandler.handle(cmd);
        LoginResp resp = authWebConverter.toLoginResp(result);
        return ApiResponse.success(resp);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        // 处理登出逻辑
        return ApiResponse.success();
    }
}