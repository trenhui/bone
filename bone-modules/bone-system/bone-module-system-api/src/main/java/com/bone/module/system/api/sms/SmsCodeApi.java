package com.bone.module.system.api.sms;

import com.bone.base.core.pojo.CommonResult;
import com.bone.module.system.api.sms.dto.code.SmsCodeValidateReqDTO;
import com.bone.module.system.api.sms.dto.code.SmsCodeSendReqDTO;
import com.bone.module.system.api.sms.dto.code.SmsCodeUseReqDTO;
import com.bone.module.system.enums.ApiConstants;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;

@FeignClient(name = ApiConstants.NAME,contextId =ApiConstants.NAME+"SmsCodeApi") // TODO 芋艿：fallbackFactory =
@Api(tags = "RPC 服务 - 短信验证码")
public interface SmsCodeApi {

    String PREFIX = ApiConstants.PREFIX + "/oauth2/sms/code";

    @PostMapping(PREFIX + "/send")
    @ApiOperation("创建短信验证码，并进行发送")
    CommonResult<Boolean> sendSmsCode(@Valid @RequestBody SmsCodeSendReqDTO reqDTO);

    @PutMapping(PREFIX + "/use")
    @ApiOperation("验证短信验证码，并进行使用")
    CommonResult<Boolean> useSmsCode(@Valid @RequestBody SmsCodeUseReqDTO reqDTO);

    @GetMapping(PREFIX + "/validate")
    @ApiOperation("检查验证码是否有效")
    CommonResult<Boolean> validateSmsCode(@Valid @RequestBody SmsCodeValidateReqDTO reqDTO);

}
