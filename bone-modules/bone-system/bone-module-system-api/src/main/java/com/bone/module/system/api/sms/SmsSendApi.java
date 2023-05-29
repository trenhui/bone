package com.bone.module.system.api.sms;

import com.bone.base.core.pojo.CommonResult;
import com.bone.module.system.api.sms.dto.send.SmsSendSingleToUserReqDTO;
import com.bone.module.system.enums.ApiConstants;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;

@FeignClient(name = ApiConstants.NAME,contextId =ApiConstants.NAME+"SmsSendApi") // TODO 芋艿：fallbackFactory =
@Api(tags = "RPC 服务 - 短信发送")
public interface SmsSendApi {

    String PREFIX = ApiConstants.PREFIX + "/sms/send";

    @PostMapping(PREFIX + "/send-single-admin")
    @ApiOperation(value = "发送单条短信给 Admin 用户", notes = "在 mobile 为空时，使用 userId 加载对应 Admin 的手机号")
    CommonResult<Long> sendSingleSmsToAdmin(@Valid @RequestBody SmsSendSingleToUserReqDTO reqDTO);

    @PostMapping(PREFIX + "/send-single-member")
    @ApiOperation(value = "发送单条短信给 Member 用户", notes = "在 mobile 为空时，使用 userId 加载对应 Member 的手机号")
    CommonResult<Long> sendSingleSmsToMember(@Valid @RequestBody SmsSendSingleToUserReqDTO reqDTO);

}
