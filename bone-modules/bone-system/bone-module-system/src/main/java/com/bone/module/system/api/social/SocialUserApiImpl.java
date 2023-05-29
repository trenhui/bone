package com.bone.module.system.api.social;

import com.bone.base.core.pojo.CommonResult;
import com.bone.module.system.api.social.dto.SocialUserBindReqDTO;
import com.bone.module.system.api.social.dto.SocialUserUnbindReqDTO;
import com.bone.module.system.service.social.SocialUserService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

import static com.bone.base.core.pojo.CommonResult.success;
import static com.bone.module.system.enums.ApiConstants.VERSION;

@RestController // 提供 RESTful API 接口，给 Feign 调用
@DubboService(version = VERSION) // 提供 Dubbo RPC 接口，给 Dubbo Consumer 调用
@Validated
public class SocialUserApiImpl implements SocialUserApi {

    @Resource
    private SocialUserService socialUserService;

    @Override
    public CommonResult<String> getAuthorizeUrl(Integer type, String redirectUri) {
        return success(socialUserService.getAuthorizeUrl(type, redirectUri));
    }

    @Override
    public CommonResult<Boolean> bindSocialUser(SocialUserBindReqDTO reqDTO) {
        socialUserService.bindSocialUser(reqDTO);
        return success(true);
    }

    @Override
    public CommonResult<Boolean> unbindSocialUser(SocialUserUnbindReqDTO reqDTO) {
        socialUserService.unbindSocialUser(reqDTO.getUserId(), reqDTO.getUserType(),
                reqDTO.getType(), reqDTO.getUnionId());
        return success(true);
    }

    @Override
    public CommonResult<Long> getBindUserId(Integer userType, Integer type, String code, String state) {
       return success(socialUserService.getBindUserId(userType, type, code, state));
    }

}
