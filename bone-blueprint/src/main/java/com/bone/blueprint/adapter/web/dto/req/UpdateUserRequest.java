package com.bone.blueprint.adapter.web.dto.req;

import lombok.Data;

/**
 * 更新用户请求DTO
 * <p>
 * 用于接收更新用户的HTTP请求
 * </p>
 */
@Data
public class UpdateUserRequest {
    /**
     * 昵称
     */
    private String nickname;
}