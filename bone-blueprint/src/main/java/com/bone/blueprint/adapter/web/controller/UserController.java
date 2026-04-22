package com.bone.blueprint.adapter.web.controller;

import com.bone.blueprint.adapter.web.assembler.UserAssembler;
import com.bone.blueprint.adapter.web.dto.req.CreateUserRequest;
import com.bone.blueprint.adapter.web.dto.req.UpdateUserRequest;
import com.bone.blueprint.adapter.web.dto.resp.UserDetailResponse;
import com.bone.blueprint.adapter.web.dto.resp.UserPageResponse;
import com.bone.blueprint.application.command.cmd.CreateUserCommand;
import com.bone.blueprint.application.command.cmd.UpdateUserCommand;
import com.bone.blueprint.application.command.handler.CreateUserCommandHandler;
import com.bone.blueprint.application.command.handler.UpdateUserCommandHandler;
import com.bone.blueprint.application.query.handler.UserDetailQueryHandler;
import com.bone.blueprint.application.query.handler.UserPageQueryHandler;
import com.bone.blueprint.application.query.qry.UserDetailQuery;
import com.bone.blueprint.application.query.qry.UserPageQuery;
import com.bone.core.result.ApiResponse;
import com.bone.core.result.PageResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final CreateUserCommandHandler createUserCommandHandler;
    private final UpdateUserCommandHandler updateUserCommandHandler;
    private final UserPageQueryHandler userPageQueryHandler;
    private final UserDetailQueryHandler userDetailQueryHandler;
    private final UserAssembler userAssembler;

    public UserController(CreateUserCommandHandler createUserCommandHandler,
                         UpdateUserCommandHandler updateUserCommandHandler,
                         UserPageQueryHandler userPageQueryHandler,
                         UserDetailQueryHandler userDetailQueryHandler,
                         UserAssembler userAssembler) {
        this.createUserCommandHandler = createUserCommandHandler;
        this.updateUserCommandHandler = updateUserCommandHandler;
        this.userPageQueryHandler = userPageQueryHandler;
        this.userDetailQueryHandler = userDetailQueryHandler;
        this.userAssembler = userAssembler;
    }

    @PostMapping
    public ApiResponse<UserDetailResponse> create(@Valid @RequestBody CreateUserRequest request) {
        CreateUserCommand command = userAssembler.toCreateUserCommand(request);
        String userId = createUserCommandHandler.handle(command);
        UserDetailResponse response = getById(userId);
        return ApiResponse.success(response);
    }

    @PutMapping("/{id}")
    public ApiResponse<UserDetailResponse> update(@PathVariable String id, @RequestBody UpdateUserRequest request) {
        UpdateUserCommand command = userAssembler.toUpdateUserCommand(id, request);
        updateUserCommandHandler.handle(command);
        UserDetailResponse response = getById(id);
        return ApiResponse.success(response);
    }

    @GetMapping("/{id}")
    public UserDetailResponse getById(@PathVariable String id) {
        UserDetailQuery query = new UserDetailQuery(id);
        return userAssembler.toUserDetailResponse(userDetailQueryHandler.handle(query));
    }

    @GetMapping
    public ApiResponse<PageResult<UserPageResponse>> page(@RequestParam int page, @RequestParam int size) {
        UserPageQuery query = new UserPageQuery(page, size);
        var pageResult = userPageQueryHandler.handle(query);
        var response = userAssembler.toUserPageResponse(pageResult);
        return ApiResponse.success(response);
    }
}
