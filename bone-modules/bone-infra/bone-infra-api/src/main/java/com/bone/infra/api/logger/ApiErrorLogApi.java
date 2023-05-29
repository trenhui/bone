package com.bone.infra.api.logger;

import com.bone.base.core.pojo.CommonResult;
import com.bone.infra.api.logger.dto.ApiErrorLogCreateReqDTO;
import com.bone.infra.enums.ApiConstants;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;

@FeignClient(name = ApiConstants.NAME+"ApiErrorLogApi",contextId=ApiConstants.NAME+"ApiErrorLogApi") // TODO 芋艿：fallbackFactory =
@Api(tags = "RPC 服务 - API 异常日志")
public interface ApiErrorLogApi {

    String PREFIX = ApiConstants.PREFIX + "/api-error-log";

    @PostMapping(PREFIX + "/create")
    @ApiOperation("创建 API 异常日志")
    CommonResult<Boolean> createApiErrorLog(@Valid @RequestBody ApiErrorLogCreateReqDTO createDTO);

}
