package com.bone.metadata.auth.adapter;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import com.bone.metadata.auth.application.convert.AuthConvert;
import com.bone.metadata.auth.application.enums.CommonStatusEnum;
import com.bone.metadata.auth.application.enums.logger.LoginLogTypeEnum;
import com.bone.metadata.auth.application.vo.auth.AuthLoginReqVO;
import com.bone.metadata.auth.sdk.entity.AuthInfoVO;
import com.bone.metadata.auth.domain.entity.permission.MenuDO;
import com.bone.metadata.auth.domain.entity.permission.RoleDO;
import com.bone.metadata.auth.domain.entity.user.AdminUserDO;
import com.bone.metadata.auth.domain.service.AdminAuthService;
import com.bone.metadata.auth.domain.service.permission.MenuService;
import com.bone.metadata.auth.domain.service.permission.PermissionService;
import com.bone.metadata.auth.domain.service.permission.RoleService;
import com.bone.metadata.auth.domain.service.user.AdminUserService;
import com.bone.core.result.Result;
import com.bone.metadata.auth.sdk.util.UserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.bone.core.result.Result.success;
import static com.bone.core.util.CollectionUtils.convertSet;

@Tag(name = "管理后台 - 认证")
@RestController
@RequestMapping("/system/auth")
@Validated
@Slf4j
public class AuthController {

    @Resource
    private AdminAuthService authService;
    @Resource
    private AdminUserService userService;
    @Resource
    private RoleService roleService;
    @Resource
    private MenuService menuService;
    @Resource
    private PermissionService permissionService;
    @Resource
    UserUtil userUtil;

    @PostMapping("/login")
    @PermitAll
    @Operation(summary = "使用账号密码登录")
    public Result<String> login(@RequestBody @Valid AuthLoginReqVO reqVO) {
        return success(authService.login(reqVO));
    }

    @PostMapping("/logout")
    @PermitAll
    @Operation(summary = "登出系统")
    public Result<Boolean> logout(HttpServletRequest request) {
        authService.logout(LoginLogTypeEnum.LOGOUT_SELF.getType());
        return success(true);
    }

    @GetMapping("/get-permission-info")
    @Operation(summary = "获取登录用户的权限信息")
    public Result<AuthInfoVO> getPermissionInfo() {
        // 1.1 获得用户信息
        String loginMobile = StpUtil.getLoginIdAsString();
        AdminUserDO user = userService.getUserByUsername(loginMobile);
        if (user == null) {
            return success(null);
        }
        // 1.2 获得角色列表
        Set<Long> roleIds = permissionService.getUserRoleIdListByUserId(user.getId());
        if (CollUtil.isEmpty(roleIds)) {
            return success(AuthConvert.INSTANCE.convert(user, Collections.emptyList(), Collections.emptyList()));
        }
        List<RoleDO> roles = roleService.getRoleList(roleIds);
        roles.removeIf(role -> !CommonStatusEnum.ENABLE.getStatus().equals(role.getStatus())); // 移除禁用的角色

        // 1.3 获得菜单列表
        Set<Long> menuIds = permissionService.getRoleMenuListByRoleId(convertSet(roles, RoleDO::getId));
        List<MenuDO> menuList = menuService.getMenuList(menuIds);
        menuList.removeIf(menu -> !CommonStatusEnum.ENABLE.getStatus().equals(menu.getStatus())); // 移除禁用的菜单

        // 2. 拼接结果返回
        return success(AuthConvert.INSTANCE.convert(user, roles, menuList));
    }

}
