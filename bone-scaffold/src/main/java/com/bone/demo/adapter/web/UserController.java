package com.bone.demo.adapter.web;

import com.bone.demo.application.UserApplicationService;
import com.bone.demo.application.dto.UserDTO;
import com.bone.demo.application.dto.query.UserQuery;
import com.bone.demo.application.dto.query.UserPageQuery;
import com.bone.demo.infrastructure.config.ApiResponse;
import com.bone.demo.infrastructure.config.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
@Tag(name = "用户管理", description = "用户相关API接口")
public class UserController {
    
    private final UserApplicationService userApplicationService;
    
    @Autowired
    public UserController(UserApplicationService userApplicationService) {
        this.userApplicationService = userApplicationService;
    }
    
    @PostMapping("/create")
    @Operation(summary = "创建用户")
    @Parameter(name = "createRequest", description = "创建用户对象", required = true)
    public ApiResponse<Long> createUser(@Validated @RequestBody UserDTO createRequest) {
        log.info("【创建用户】请求参数: {}", createRequest);
        Long userId = userApplicationService.createUser(createRequest);
        log.info("【创建用户】成功，用户ID: {}", userId);
        return ApiResponse.success("创建用户成功", userId);
    }
    
    @PutMapping("/update")
    @Operation(summary = "更新用户", description = "更新指定用户的信息")
    @Parameter(name = "updateRequest", description = "更新用户对象", required = true)
    public ApiResponse<Boolean> updateUser(@Validated @RequestBody UserDTO updateRequest) {
        log.info("【更新用户】请求参数: {}", updateRequest);
        Boolean result = userApplicationService.updateUser(updateRequest);
        log.info("【更新用户】成功，获得用户ID: {}", updateRequest.getId());
        return ApiResponse.success(result);
    }
    
    @DeleteMapping("/delete")
    @Operation(summary = "删除用户", description = "根据ID删除指定用户")
    public ApiResponse<Boolean> deleteUser(
            @Parameter(description = "用户ID", required = true, example = "1001")
            @RequestParam("id") Long userId) {
        log.info("【删除用户】ID: {}", userId);
        Boolean result = userApplicationService.deleteUser(userId);
        log.info("【删除用户】成功，ID: {}", userId);
        return ApiResponse.success(result);
    }
    
    @GetMapping("/get")
    @Operation(summary = "获得用户")
    @Parameter(name = "id", description = "主键", required = true, example = "1024")
    public ApiResponse<UserDTO> getUser(@RequestParam("id") Long id) {
        UserDTO userDTO = userApplicationService.getUser(id);
        return ApiResponse.success(userDTO);
    }
    
    @GetMapping("/list")
    @Operation(summary = "查询用户")
    @Parameter(name = "userQuery", description = "用户查询对象", required = true)
    public ApiResponse<List<UserDTO>> queryUser(@RequestBody UserQuery userQuery) {
        List<UserDTO> userList = userApplicationService.queryUser(userQuery);
        return ApiResponse.success(userList);
    }
    
    @GetMapping("/page")
    @Operation(summary = "分页查询用户")
    @Parameter(name = "userPageQuery", description = "分页用户查询对象", required = true)
    public ApiResponse<PageResult<UserDTO>> queryUserPage(@RequestBody UserPageQuery userPageQuery) {
        PageResult<UserDTO> userPageResult = userApplicationService.queryUserPage(userPageQuery);
        return ApiResponse.success(userPageResult);
    }
}