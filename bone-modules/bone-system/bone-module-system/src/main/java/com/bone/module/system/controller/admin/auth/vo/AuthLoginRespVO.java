package com.bone.module.system.controller.admin.auth.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@ApiModel("管理后台 - 登录 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthLoginRespVO {

    @ApiModelProperty(value = "用户编号", required = true, example = "1024")
    private Long userId;

    @ApiModelProperty(value = "访问令牌", required = true, example = "happy")
    private String accessToken;

    @ApiModelProperty(value = "刷新令牌", required = true, example = "nice")
    private String refreshToken;

    @ApiModelProperty(value = "过期时间", required = true)
    private LocalDateTime expiresTime;

    @ApiModelProperty(value = "用户类型", required = true, example = "2")
    private Integer loginUserType;

    @ApiModelProperty(value = "租户ID", required = true, example = "1")
    private Long tenantId;

    @ApiModelProperty(value = "状态", required = true, example = "1")
    private String status;

    @ApiModelProperty(value = "类型", required = true, example = "1")
    private String type;

    @ApiModelProperty(value = "认证信息", required = true, example = "1")
    private String currentAuthority;
}
