package com.bone.module.system.api.user;

import com.bone.base.core.pojo.CommonResult;
import com.bone.base.core.util.collection.CollectionUtils;
import com.bone.module.system.api.user.dto.AdminUserRespDTO;
import com.bone.module.system.enums.ApiConstants;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@FeignClient(name = ApiConstants.NAME,contextId =ApiConstants.NAME+"AdminUserApi") // TODO 芋艿：fallbackFactory =
@Api(tags = "RPC 服务 - 管理员用户")
public interface AdminUserApi {

    String PREFIX = ApiConstants.PREFIX + "/user";

    @GetMapping(PREFIX + "/get")
    @ApiOperation("通过用户 ID 查询用户")
    @ApiImplicitParam(name = "id", value = "用户编号", example = "1", required = true, dataTypeClass = Long.class)
    CommonResult<AdminUserRespDTO> getUser(@RequestParam("id") Long id);

    @GetMapping(PREFIX + "/list")
    @ApiOperation("通过用户 ID 查询用户们")
    @ApiImplicitParam(name = "ids", value = "部门编号数组", example = "1,2", required = true, allowMultiple = true)
    CommonResult<List<AdminUserRespDTO>> getUserList(@RequestParam("ids") Collection<Long> ids);

    @GetMapping(PREFIX + "/list-by-dept-id")
    @ApiOperation("获得指定部门的用户数组")
    @ApiImplicitParam(name = "deptIds", value = "部门编号数组", example = "1,2", required = true, allowMultiple = true)
    CommonResult<List<AdminUserRespDTO>> getUserListByDeptIds(@RequestParam("deptIds") Collection<Long> deptIds);

    @GetMapping(PREFIX + "/list-by-post-id")
    @ApiOperation("获得指定岗位的用户数组")
    @ApiImplicitParam(name = "postIds", value = "岗位编号数组", example = "2,3", required = true, allowMultiple = true)
    CommonResult<List<AdminUserRespDTO>> getUserListByPostIds(@RequestParam("postIds") Collection<Long> postIds);

    /**
     * 获得用户 Map
     *
     * @param ids 用户编号数组
     * @return 用户 Map
     */
    default Map<Long, AdminUserRespDTO> getUserMap(Collection<Long> ids) {
        return CollectionUtils.convertMap(getUserList(ids).getCheckedData(), AdminUserRespDTO::getId);
    }

    @GetMapping(PREFIX + "/valid")
    @ApiOperation("校验用户们是否有效")
    @ApiImplicitParam(name = "ids", value = "用户编号数组", example = "3,5", required = true)
    CommonResult<Boolean> validUserList(@RequestParam("ids") Set<Long> ids);

}
