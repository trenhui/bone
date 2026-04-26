package com.pkh.cloud.auth.sdk.core.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import com.bone.core.result.Result;
import com.pkh.cloud.auth.sdk.constant.AuthRedisConstant;
import com.pkh.cloud.auth.sdk.core.feign.PermissionFeign;
import com.pkh.cloud.auth.sdk.entity.AuthInfoVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class BonePermissionService implements StpInterface {
    @Autowired
    AuthRedisConstant authRedisConstant;
    @Autowired
    PermissionFeign permissionFeign;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        String token = StpUtil.getTokenValueByLoginId(loginId, null);
        AuthInfoVO permissionInfo = getPermissionInfo(token);
        log.info("getPermissionList:{}",permissionInfo);
        return new ArrayList<>(permissionInfo.getPermissions());
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        String token = StpUtil.getTokenValueByLoginId(loginId, null);
        AuthInfoVO permissionInfo = getPermissionInfo(token);
        log.info("getRoleList:{}",permissionInfo);
        return new ArrayList<>(permissionInfo.getRoles());
    }

    private AuthInfoVO getPermissionInfo(String token) {
        String authInfoStr = StpUtil.getSession().getString(SaSession.USER);
        // 已有信息 直接返回
        if (StringUtils.hasText(authInfoStr)){
            return JSONUtil.toBean(authInfoStr, AuthInfoVO.class);
        }
        AuthInfoVO result = new AuthInfoVO();
        if (!StringUtils.hasText(token)){
            return result;
        }
        // 未获取信息 则调用接口获取
        Result<AuthInfoVO> authInfoVOResult = permissionFeign.getPermissionInfo(token);
        if (authInfoVOResult.getSuccess()) {
            StpUtil.getSession().set(SaSession.USER,JSONUtil.toJsonStr(authInfoVOResult.getData()));
            return authInfoVOResult.getData();
        } else {
            log.error("getPermissionInfo error:{}",authInfoVOResult.getMessage());
            return result;
        }
    }


}
