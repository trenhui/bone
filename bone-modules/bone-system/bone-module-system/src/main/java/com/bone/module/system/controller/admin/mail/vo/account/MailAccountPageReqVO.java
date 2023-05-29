package com.bone.module.system.controller.admin.mail.vo.account;

import com.bone.base.core.pojo.PageParam;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@ApiModel("管理后台 - 邮箱账号分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MailAccountPageReqVO extends PageParam {

    @ApiModelProperty(value = "邮箱", required = true, example = "boneyuanma@123.com")
    private String mail;

    @ApiModelProperty(value = "用户名" , required = true , example = "bone")
    private String username;

}
