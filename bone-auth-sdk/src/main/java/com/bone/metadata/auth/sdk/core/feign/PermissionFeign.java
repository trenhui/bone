package com.bone.metadata.auth.sdk.core.feign;

import com.bone.core.result.Result;
import com.bone.metadata.auth.sdk.entity.AuthInfoVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "bone-auth-server",contextId = "permissionFeign")
public interface PermissionFeign {
    /**
     * 获取当前登录用户的权限信息
     * @param token 使用 StpUtil.getTokenValue() 获取到的 token
     * @return
     */
    @GetMapping("/system/auth/get-permission-info")
    Result<AuthInfoVO> getPermissionInfo(@RequestHeader("Satoken") String token);
}
