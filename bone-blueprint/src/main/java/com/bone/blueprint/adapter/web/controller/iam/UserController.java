package com.bone.blueprint.adapter.web.controller.iam;

import com.bone.blueprint.application.command.cmd.iam.CreateUserCmd;
import com.bone.blueprint.application.command.handler.iam.CreateUserHandler;
import com.bone.core.result.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/iam/users")
@RequiredArgsConstructor
public class UserController {
    private final CreateUserHandler createUserHandler;
    
    @PostMapping
    public ApiResponse<Long> createUser(@RequestBody CreateUserCmd cmd) {
        Long id = createUserHandler.handle(cmd);
        return ApiResponse.success(id);
    }
}
