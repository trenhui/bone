package com.bone.module.system.api.user;

import com.bone.base.core.pojo.CommonResult;
import com.bone.module.system.api.user.dto.AdminUserRespDTO;
import com.bone.module.system.convert.user.UserConvert;
import com.bone.module.system.dal.dataobject.user.AdminUserDO;
import com.bone.module.system.service.user.AdminUserService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.bone.base.core.pojo.CommonResult.success;
import static com.bone.module.system.enums.ApiConstants.VERSION;

@RestController // 提供 RESTful API 接口，给 Feign 调用
@DubboService(version = VERSION) // 提供 Dubbo RPC 接口，给 Dubbo Consumer 调用
@Validated
public class AdminUserApiImpl implements AdminUserApi {

    @Resource
    private AdminUserService userService;

    @Override
    public CommonResult<AdminUserRespDTO> getUser(Long id) {
        AdminUserDO user = userService.getUser(id);
        return success(UserConvert.INSTANCE.convert4(user));
    }

    @Override
    public CommonResult<List<AdminUserRespDTO>> getUserList(Collection<Long> ids) {
        List<AdminUserDO> users = userService.getUserList(ids);
        return success(UserConvert.INSTANCE.convertList4(users));
    }

    @Override
    public CommonResult<List<AdminUserRespDTO>> getUserListByDeptIds(Collection<Long> deptIds) {
        List<AdminUserDO> users = userService.getUserListByDeptIds(deptIds);
        return success(UserConvert.INSTANCE.convertList4(users));
    }

    @Override
    public CommonResult<List<AdminUserRespDTO>> getUserListByPostIds(Collection<Long> postIds) {
        List<AdminUserDO> users = userService.getUserListByPostIds(postIds);
        return success(UserConvert.INSTANCE.convertList4(users));
    }

    @Override
    public CommonResult<Boolean> validUserList(Set<Long> ids) {
        userService.validateUserList(ids);
        return success(true);
    }

}
