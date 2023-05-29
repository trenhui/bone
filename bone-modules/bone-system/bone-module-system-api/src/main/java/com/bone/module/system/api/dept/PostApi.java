package com.bone.module.system.api.dept;

import com.bone.base.core.pojo.CommonResult;
import com.bone.module.system.enums.ApiConstants;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;

@FeignClient(name = ApiConstants.NAME,contextId =ApiConstants.NAME+"PostApi") // TODO 芋艿：fallbackFactory =
@Api(tags = "RPC 服务 - 岗位")
public interface PostApi {

    String PREFIX = ApiConstants.PREFIX + "/post";

    @GetMapping(PREFIX + "/valid")
    @ApiOperation("校验岗位是否合法")
    @ApiImplicitParam(name = "ids", value = "岗位编号数组", example = "1,2", required = true, allowMultiple = true)
    CommonResult<Boolean> validPostList(@RequestParam("ids") Collection<Long> ids);

}
