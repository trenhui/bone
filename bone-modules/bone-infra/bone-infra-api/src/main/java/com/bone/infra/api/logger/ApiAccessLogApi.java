package com.bone.infra.api.logger;

import com.bone.base.core.pojo.CommonResult;
import com.bone.infra.api.logger.dto.ApiAccessLogCreateReqDTO;
import com.bone.infra.enums.ApiConstants;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;

@FeignClient(name = ApiConstants.NAME+"ApiAccessLogApi",contextId=ApiConstants.NAME+"ApiAccessLogApi") // TODO 芋艿：fallbackFactory =
@Api(tags = "RPC 服务 - API 访问日志")
public interface ApiAccessLogApi {

    String PREFIX = ApiConstants.PREFIX + "/api-access-log";

    @PostMapping(PREFIX + "/create")
    @ApiOperation("创建 API 访问日志")
    CommonResult<Boolean> createApiAccessLog(@Valid @RequestBody ApiAccessLogCreateReqDTO createDTO);

}
