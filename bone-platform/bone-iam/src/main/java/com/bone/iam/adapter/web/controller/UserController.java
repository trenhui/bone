package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.iam.application.command.cmd.CreateUserCmd;
import com.bone.iam.application.command.cmd.UpdateUserCmd;
import com.bone.iam.application.command.handler.CreateUserHandler;
import com.bone.iam.application.command.handler.UpdateUserHandler;
import com.bone.iam.application.query.dto.UserDTO;
import com.bone.iam.application.query.handler.UserPageQueryHandler;
import com.bone.iam.application.query.qry.UserPageQry;
import com.bone.iam.adapter.web.dto.req.CreateUserReq;
import com.bone.iam.adapter.web.dto.req.UpdateUserReq;
import com.bone.iam.adapter.web.dto.resp.UserDetailResp;
import com.bone.iam.adapter.web.converter.UserWebConverter;
import com.bone.iam.domain.model.user.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/iam/users")
@RequiredArgsConstructor
public class UserController {
    private final CreateUserHandler createUserHandler;
    private final UpdateUserHandler updateUserHandler;
    private final UserPageQueryHandler userPageQueryHandler;
    private final UserWebConverter userWebConverter;

    @PostMapping
    public ApiResponse<String> create(@RequestBody CreateUserReq req) {
        CreateUserCmd cmd = userWebConverter.toCreateUserCmd(req);
        UserId userId = createUserHandler.handle(cmd);
        return ApiResponse.success(userId.value());
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable String id, @RequestBody UpdateUserReq req) {
        UpdateUserCmd cmd = userWebConverter.toUpdateUserCmd(id, req);
        updateUserHandler.handle(cmd);
        return ApiResponse.success();
    }

    @GetMapping
    public ApiResponse<PageResult<UserDTO>> page(UserPageQry qry) {
        PageResult<UserDTO> result = userPageQueryHandler.handle(qry);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<UserDetailResp> detail(@PathVariable String id) {
        // 实现获取用户详情逻辑
        return ApiResponse.success(new UserDetailResp());
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<Void> enable(@PathVariable String id) {
        // 实现启用用户逻辑
        return ApiResponse.success();
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable String id) {
        // 实现禁用用户逻辑
        return ApiResponse.success();
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable String id) {
        // 实现重置密码逻辑
        return ApiResponse.success();
    }
}