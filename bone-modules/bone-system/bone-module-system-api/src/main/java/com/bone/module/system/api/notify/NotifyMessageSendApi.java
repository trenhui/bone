package com.bone.module.system.api.notify;

import com.bone.base.core.pojo.CommonResult;
import com.bone.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import com.bone.module.system.enums.ApiConstants;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.validation.Valid;

@FeignClient(name = ApiConstants.NAME,contextId =ApiConstants.NAME+"NotifyMessageSendApi") // TODO 芋艿：fallbackFactory =
@Api(tags = "RPC 服务 - 站内信发送")
public interface NotifyMessageSendApi {

    String PREFIX = ApiConstants.PREFIX + "/notify/send";

    @PostMapping(PREFIX + "/send-single-admin")
    @ApiOperation(value = "发送单条站内信给 Admin 用户")
    CommonResult<Long> sendSingleMessageToAdmin(@Valid NotifySendSingleToUserReqDTO reqDTO);

    @PostMapping(PREFIX + "/send-single-member")
    @ApiOperation(value = "发送单条站内信给 Member 用户")
    CommonResult<Long> sendSingleMessageToMember(@Valid NotifySendSingleToUserReqDTO reqDTO);

}
