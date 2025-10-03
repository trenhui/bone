package com.bone.metadata.auth.adapter;

import cn.hutool.core.collection.CollUtil;
import com.bone.metadata.auth.application.convert.UserConvert;
import com.bone.metadata.auth.application.vo.user.UserProfileRespVO;
import com.bone.metadata.auth.application.vo.user.UserProfileUpdatePasswordReqVO;
import com.bone.metadata.auth.application.vo.user.UserProfileUpdateReqVO;
import com.bone.metadata.auth.domain.entity.dept.DeptDO;
import com.bone.metadata.auth.domain.entity.dept.PostDO;
import com.bone.metadata.auth.domain.entity.permission.RoleDO;
import com.bone.metadata.auth.domain.entity.user.AdminUserDO;
import com.bone.metadata.auth.domain.service.dept.DeptService;
import com.bone.metadata.auth.domain.service.dept.PostService;
import com.bone.metadata.auth.domain.service.permission.PermissionService;
import com.bone.metadata.auth.domain.service.permission.RoleService;
import com.bone.metadata.auth.domain.service.user.AdminUserService;
import com.bone.core.result.Result;
import com.bone.core.tenant.context.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

import static com.bone.core.result.Result.success;

public class UserProfileController {
    @Resource
    private AdminUserService userService;
    @Resource
    private DeptService deptService;
    @Resource
    private PostService postService;
    @Resource
    private PermissionService permissionService;
    @Resource
    private RoleService roleService;

    @GetMapping("/get")
    @Operation(summary = "获得登录用户信息")
    public Result<UserProfileRespVO> getUserProfile() {
        // 获得用户基本信息
        AdminUserDO user = userService.getUser(UserContext.getCurrentUser().getId());
        // 获得用户角色
        List<RoleDO> userRoles = roleService.getRoleListFromCache(permissionService.getUserRoleIdListByUserId(user.getId()));
        // 获得部门信息
        DeptDO dept = user.getDeptId() != null ? deptService.getDept(user.getDeptId()) : null;
        // 获得岗位信息
        List<PostDO> posts = CollUtil.isNotEmpty(user.getPostIds()) ? postService.getPostList(user.getPostIds()) : null;
        return success(UserConvert.INSTANCE.convert(user, userRoles, dept, posts));
    }

    @PutMapping("/update")
    @Operation(summary = "修改用户个人信息")
    public Result<Boolean> updateUserProfile(@Valid @RequestBody UserProfileUpdateReqVO reqVO) {
        userService.updateUserProfile(UserContext.getCurrentUser().getId(), reqVO);
        return success(true);
    }

    @PutMapping("/update-password")
    @Operation(summary = "修改用户个人密码")
    public Result<Boolean> updateUserProfilePassword(@Valid @RequestBody UserProfileUpdatePasswordReqVO reqVO) {
        userService.updateUserPassword(UserContext.getCurrentUser().getId(), reqVO);
        return success(true);
    }


}
