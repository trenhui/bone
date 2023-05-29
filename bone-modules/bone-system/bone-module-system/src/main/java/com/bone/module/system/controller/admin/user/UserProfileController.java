package com.bone.module.system.controller.admin.user;

import cn.hutool.core.collection.CollUtil;
import com.bone.base.core.enums.UserTypeEnum;
import com.bone.base.core.pojo.CommonResult;
import com.bone.base.datapermission.core.annotation.DataPermission;
import com.bone.module.system.controller.admin.user.vo.profile.UserProfileRespVO;
import com.bone.module.system.controller.admin.user.vo.profile.UserProfileUpdatePasswordReqVO;
import com.bone.module.system.controller.admin.user.vo.profile.UserProfileUpdateReqVO;
import com.bone.module.system.convert.user.UserConvert;
import com.bone.module.system.dal.dataobject.dept.DeptDO;
import com.bone.module.system.dal.dataobject.dept.PostDO;
import com.bone.module.system.dal.dataobject.permission.RoleDO;
import com.bone.module.system.dal.dataobject.social.SocialUserDO;
import com.bone.module.system.dal.dataobject.user.AdminUserDO;
import com.bone.module.system.service.dept.DeptService;
import com.bone.module.system.service.dept.PostService;
import com.bone.module.system.service.permission.PermissionService;
import com.bone.module.system.service.permission.RoleService;
import com.bone.module.system.service.social.SocialUserService;
import com.bone.module.system.service.user.AdminUserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;

import static com.bone.base.core.exception.util.ServiceExceptionUtil.exception;
import static com.bone.base.core.pojo.CommonResult.success;
import static com.bone.base.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static com.bone.infra.enums.ErrorCodeConstants.FILE_IS_EMPTY;

@Api(tags = "管理后台 - 用户个人中心")
@RestController
@RequestMapping("/system/user/profile")
@Validated
@Slf4j
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
    @Resource
    private SocialUserService socialService;

    @GetMapping("/get")
    @ApiOperation("获得登录用户信息")
    @DataPermission(enable = false) // 关闭数据权限，避免只查看自己时，查询不到部门。
    public CommonResult<UserProfileRespVO> profile() {
        // 获得用户基本信息
        AdminUserDO user = userService.getUser(getLoginUserId());
        UserProfileRespVO resp = UserConvert.INSTANCE.convert03(user);
        // 获得用户角色
        List<RoleDO> userRoles = roleService.getRoleListFromCache(permissionService.getUserRoleIdListByUserId(user.getId()));
        resp.setRoles(UserConvert.INSTANCE.convertList(userRoles));
        // 获得部门信息
        if (user.getDeptId() != null) {
            DeptDO dept = deptService.getDept(user.getDeptId());
            resp.setDept(UserConvert.INSTANCE.convert02(dept));
        }
        // 获得岗位信息
        if (CollUtil.isNotEmpty(user.getPostIds())) {
            List<PostDO> posts = postService.getPostList(user.getPostIds());
            resp.setPosts(UserConvert.INSTANCE.convertList02(posts));
        }
        // 获得社交用户信息
        List<SocialUserDO> socialUsers = socialService.getSocialUserList(user.getId(), UserTypeEnum.ADMIN.getValue());
        resp.setSocialUsers(UserConvert.INSTANCE.convertList03(socialUsers));
        return success(resp);
    }

    @PutMapping("/update")
    @ApiOperation("修改用户个人信息")
    public CommonResult<Boolean> updateUserProfile(@Valid @RequestBody UserProfileUpdateReqVO reqVO) {
        userService.updateUserProfile(getLoginUserId(), reqVO);
        return success(true);
    }

    @PutMapping("/update-password")
    @ApiOperation("修改用户个人密码")
    public CommonResult<Boolean> updateUserProfilePassword(@Valid @RequestBody UserProfileUpdatePasswordReqVO reqVO) {
        userService.updateUserPassword(getLoginUserId(), reqVO);
        return success(true);
    }

    @PutMapping("/update-avatar")
    @ApiOperation("上传用户个人头像")
    public CommonResult<String> updateUserAvatar(@RequestParam("avatarFile") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw exception(FILE_IS_EMPTY);
        }
        String avatar = userService.updateUserAvatar(getLoginUserId(), file.getInputStream());
        return success(avatar);
    }

}
