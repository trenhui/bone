package com.bone.metadata.auth.domain.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.ObjectUtil;
import com.bone.metadata.auth.application.enums.CommonStatusEnum;
import com.bone.metadata.auth.application.enums.UserTypeEnum;
import com.bone.metadata.auth.application.enums.logger.LoginLogTypeEnum;
import com.bone.metadata.auth.application.enums.logger.LoginResultEnum;
import com.bone.metadata.auth.application.vo.auth.AuthLoginReqVO;
import com.bone.metadata.auth.application.vo.logger.LoginLogCreateReqDTO;
import com.bone.metadata.auth.domain.entity.user.AdminUserDO;
import com.bone.metadata.auth.domain.mapper.permission.RoleMenuMapper;
import com.bone.metadata.auth.domain.service.logger.LoginLogService;
import com.bone.metadata.auth.domain.service.permission.MenuService;
import com.bone.metadata.auth.domain.service.permission.PermissionService;
import com.bone.metadata.auth.domain.service.permission.RoleService;
import com.bone.metadata.auth.domain.service.user.AdminUserService;
import com.bone.metadata.auth.infrastructure.util.ServletUtils;
import com.bone.core.exception.BizException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.*;

import static com.bone.metadata.auth.application.enums.ErrorCodeConstants.AUTH_LOGIN_BAD_CREDENTIALS;
import static com.bone.metadata.auth.application.enums.ErrorCodeConstants.AUTH_LOGIN_USER_DISABLED;


/**
 * Auth Service 实现类
 *
 * @author 
 */
@Service
@Slf4j
public class AdminAuthServiceImpl implements AdminAuthService {

    @Resource
    private AdminUserService userService;
    @Resource
    private LoginLogService loginLogService;
    @Resource
    private PermissionService permissionService;
    @Resource
    private RoleService roleService;
    @Resource
    private MenuService menuService;

    /**
     * 验证码的开关，默认为 true
     */
    @Value("${yudao.captcha.enable:true}")
    private Boolean captchaEnable;
    @Autowired
    private RoleMenuMapper roleMenuMapper;

    @Override
    public AdminUserDO authenticate(String username, String password) {
        final LoginLogTypeEnum logTypeEnum = LoginLogTypeEnum.LOGIN_USERNAME;
        // 校验账号是否存在
        AdminUserDO user = userService.getUserByUsername(username);
        if (user == null) {
            createLoginLog(null, username, logTypeEnum, LoginResultEnum.BAD_CREDENTIALS);
            throw new BizException(AUTH_LOGIN_BAD_CREDENTIALS.getCode(),AUTH_LOGIN_BAD_CREDENTIALS.getMsg());
        }
        if (!userService.isPasswordMatch(password, user.getPassword())) {
            createLoginLog(user.getId(), username, logTypeEnum, LoginResultEnum.BAD_CREDENTIALS);
            throw new BizException(AUTH_LOGIN_BAD_CREDENTIALS.getCode(),AUTH_LOGIN_BAD_CREDENTIALS.getMsg());
        }
        // 校验是否禁用
        if (CommonStatusEnum.isDisable(user.getStatus())) {
            createLoginLog(user.getId(), username, logTypeEnum, LoginResultEnum.USER_DISABLED);
            throw new BizException(AUTH_LOGIN_USER_DISABLED.getCode(),AUTH_LOGIN_USER_DISABLED.getMsg());
        }
        return user;
    }

    @Override
    public String login(AuthLoginReqVO reqVO) {
        // 使用账号密码，进行登录
        AdminUserDO user = authenticate(reqVO.getUsername(), reqVO.getPassword());

        // 创建 Token 令牌,记录登录日志
        return createTokenAfterLoginSuccess(user, LoginLogTypeEnum.LOGIN_USERNAME);
    }

    private void createLoginLog(Long userId, String username,
                                LoginLogTypeEnum logTypeEnum, LoginResultEnum loginResult) {
        // 插入登录日志
        LoginLogCreateReqDTO reqDTO = new LoginLogCreateReqDTO();
        reqDTO.setLogType(logTypeEnum.getType());
        reqDTO.setUserId(userId);
        reqDTO.setUserType(getUserType().getValue());
        reqDTO.setUsername(username);
        reqDTO.setUserAgent(ServletUtils.getUserAgent());
        reqDTO.setUserIp(ServletUtils.getClientIP());
        reqDTO.setResult(loginResult.getResult());
        loginLogService.createLoginLog(reqDTO);
        // 更新最后登录时间
        if (userId != null && Objects.equals(LoginResultEnum.SUCCESS.getResult(), loginResult.getResult())) {
            userService.updateUserLogin(userId, ServletUtils.getClientIP());
        }
    }

    private String createTokenAfterLoginSuccess(AdminUserDO user, LoginLogTypeEnum logType) {
        //校验
        Assert.notNull(user, "用户不存在");
        Assert.notNull(user.getMobile(), "用户不存在");
        Assert.notNull(user.getUsername(), "用户不存在");
        // 登录
        StpUtil.login(user.getUsername());
        // 插入登陆日志
        createLoginLog(user.getId(), user.getUsername(), logType, LoginResultEnum.SUCCESS);
        // 构建返回结果
        return StpUtil.getTokenValue();
    }

    @Override
    public void logout(Integer logType) {
        if(Objects.isNull(StpUtil.getLoginIdByToken(StpUtil.getTokenValue()))){
            return;
        }
        String username  = StpUtil.getLoginIdByToken(StpUtil.getTokenValue()).toString();
        AdminUserDO userDO = userService.getUserByUsername(username);
        StpUtil.logout();
        // 删除成功，则记录登出日志
        createLogoutLog(userDO.getId(), UserTypeEnum.MEMBER.getValue(), logType);
    }

    private void createLogoutLog(Long userId, Integer userType, Integer logType) {
        LoginLogCreateReqDTO reqDTO = new LoginLogCreateReqDTO();
        reqDTO.setLogType(logType);
        reqDTO.setUserId(userId);
        reqDTO.setUserType(userType);
        if (ObjectUtil.equal(getUserType().getValue(), userType)) {
            reqDTO.setUsername(getUsername(userId));
        } else {
            reqDTO.setUsername(getUsername(userId));
        }
        reqDTO.setUserAgent(ServletUtils.getUserAgent());
        reqDTO.setUserIp(ServletUtils.getClientIP());
        reqDTO.setResult(LoginResultEnum.SUCCESS.getResult());
        loginLogService.createLoginLog(reqDTO);
    }

    private String getUsername(Long userId) {
        if (userId == null) {
            return null;
        }
        AdminUserDO user = userService.getUser(userId);
        return user != null ? user.getUsername() : null;
    }

    private UserTypeEnum getUserType() {
        return UserTypeEnum.ADMIN;
    }


}
