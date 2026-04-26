package com.pkh.cloud.auth.sdk.util;

import cn.dev33.satoken.stp.StpUtil;
import com.pkh.cloud.auth.sdk.core.service.BonePermissionService;
import lombok.experimental.UtilityClass;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserUtil {
    @Autowired
    private BonePermissionService bonePermissionService;

    /**
     * 获取用户Code
     * @return
     */
    public String getUserCode(){
        return StpUtil.getLoginIdAsString();
    }

    /**
     * 判断是否登录
     * @return
     */
    public Boolean isLogin(){
        try {
            StpUtil.checkLogin();
        } catch (Exception e){
            return false;
        }
        return true;
    }

    /**
     * 获取用户权限
     * @return
     */
    public List<String> getPermissionList(){
        return bonePermissionService.getPermissionList(StpUtil.getLoginId(), null);
    }

    public List<String> getRoleList(){
        return bonePermissionService.getRoleList(StpUtil.getLoginId(), null);
    }

}
