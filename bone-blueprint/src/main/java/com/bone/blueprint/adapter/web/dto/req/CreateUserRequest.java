package com.bone.blueprint.adapter.web.dto.req;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;

/**
 * 创建用户请求DTO
 * <p>
 * 用于接收创建用户的HTTP请求
 * </p>
 */
@Data
public class CreateUserRequest {
    /**
     * 用户名
     */
    @NotEmpty(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度必须在3-20之间")
    private String username;
    
    /**
     * 密码
     */
    @NotEmpty(message = "密码不能为空")
    @Size(min = 6, message = "密码长度不能少于6位")
    private String password;
    
    /**
     * 昵称
     */
    private String nickname;
}