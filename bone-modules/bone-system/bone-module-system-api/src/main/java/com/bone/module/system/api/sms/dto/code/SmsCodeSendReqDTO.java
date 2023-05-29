package com.bone.module.system.api.sms.dto.code;

import com.bone.base.core.validation.InEnum;
import com.bone.base.core.validation.Mobile;
import com.bone.module.system.enums.sms.SmsSceneEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@ApiModel("RPC 服务 - 短信验证码的发送 Request DTO")
@Data
public class SmsCodeSendReqDTO {

    @ApiModelProperty(value = "手机号", required = true, example = "15601691300")
    @Mobile
    @NotEmpty(message = "手机号不能为空")
    private String mobile;
    @ApiModelProperty(value = "发送场景", required = true, example = "1")
    @NotNull(message = "发送场景不能为空")
    @InEnum(SmsSceneEnum.class)
    private Integer scene;
    @ApiModelProperty(value = "发送 IP", required = true, example = "10.20.30.40")
    @NotEmpty(message = "发送 IP 不能为空")
    private String createIp;

}
