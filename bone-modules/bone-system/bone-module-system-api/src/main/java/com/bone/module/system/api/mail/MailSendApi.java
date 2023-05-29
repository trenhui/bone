package com.bone.module.system.api.mail;

import com.bone.base.core.pojo.CommonResult;
import com.bone.module.system.api.mail.dto.MailSendSingleToUserReqDTO;
import com.bone.module.system.enums.ApiConstants;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.validation.Valid;

@FeignClient(name = ApiConstants.NAME,contextId =ApiConstants.NAME+"MailSendApi") // TODO 芋艿：fallbackFactory =
@Api(tags = "RPC 服务 - 邮件发送")
public interface MailSendApi {

    String PREFIX = ApiConstants.PREFIX + "/mail/send";

    @PostMapping(PREFIX + "/send-single-admin")
    @ApiOperation(value = "发送单条邮件给 Admin 用户", notes = "在 mail 为空时，使用 userId 加载对应 Admin 的邮箱")
    CommonResult<Long> sendSingleMailToAdmin(@Valid MailSendSingleToUserReqDTO reqDTO);

    @PostMapping(PREFIX + "/send-single-member")
    @ApiOperation(value = "发送单条邮件给 Member 用户", notes = "在 mail 为空时，使用 userId 加载对应 Member 的邮箱")
    CommonResult<Long> sendSingleMailToMember(@Valid MailSendSingleToUserReqDTO reqDTO);

}
