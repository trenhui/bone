package com.bone.module.system.api.tenant;

import com.bone.base.core.pojo.CommonResult;
import com.bone.module.system.enums.ApiConstants;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = ApiConstants.NAME,contextId =ApiConstants.NAME+"TenantApi") // TODO 芋艿：fallbackFactory =
@Api(tags = "RPC 服务 - 多租户")
public interface TenantApi {

    String PREFIX = ApiConstants.PREFIX + "/tenant";

    @GetMapping(PREFIX + "/id-list")
    @ApiOperation("获得所有租户编号")
    CommonResult<List<Long>> getTenantIdList();

    @GetMapping(PREFIX + "/valid")
    @ApiOperation("校验租户是否合法")
    @ApiImplicitParam(name = "id", value = "租户编号", required = true, example = "1024", dataTypeClass = Long.class)
    CommonResult<Boolean> validTenant(@RequestParam("id") Long id);

}
