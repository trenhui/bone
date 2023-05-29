package com.bone.module.system.controller.admin.notify.vo.template;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@ApiModel("管理后台 - 站内信模板的发送 Request VO")
@Data
public class NotifyTemplateSendReqVO {

    @ApiModelProperty(value = "用户id", required = true, example = "01")
    @NotNull(message = "用户id不能为空")
    private Long userId;

    @ApiModelProperty(value = "模板编码", required = true, example = "01")
    @NotEmpty(message = "模板编码不能为空")
    private String templateCode;

    @ApiModelProperty(value = "模板参数")
    private Map<String, Object> templateParams;
}
