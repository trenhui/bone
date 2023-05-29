package com.bone.module.system.api.oauth2;

import com.bone.base.core.pojo.CommonResult;
import com.bone.module.system.api.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import com.bone.module.system.api.oauth2.dto.OAuth2AccessTokenCreateReqDTO;
import com.bone.module.system.api.oauth2.dto.OAuth2AccessTokenRespDTO;
import com.bone.module.system.convert.auth.OAuth2TokenConvert;
import com.bone.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import com.bone.module.system.service.oauth2.OAuth2TokenService;
import io.swagger.annotations.ApiOperation;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

import static com.bone.base.core.pojo.CommonResult.success;
import static com.bone.module.system.enums.ApiConstants.VERSION;

@RestController // 提供 RESTful API 接口，给 Feign 调用
@DubboService(version = VERSION) // 提供 Dubbo RPC 接口，给 Dubbo Consumer 调用
@Validated
public class OAuth2TokenApiImpl implements OAuth2TokenApi {

    @Resource
    private OAuth2TokenService oauth2TokenService;

    @Override
    @ApiOperation("创建访问令牌")
    public CommonResult<OAuth2AccessTokenRespDTO> createAccessToken(OAuth2AccessTokenCreateReqDTO reqDTO) {
        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.createAccessToken(
                reqDTO.getUserId(), reqDTO.getUserType(), reqDTO.getClientId(), reqDTO.getScopes());
        return success(OAuth2TokenConvert.INSTANCE.convert2(accessTokenDO));
    }

    @Override
    public CommonResult<OAuth2AccessTokenCheckRespDTO> checkAccessToken(String accessToken) {
        return success(OAuth2TokenConvert.INSTANCE.convert(oauth2TokenService.checkAccessToken(accessToken)));
    }

    @Override
    public CommonResult<OAuth2AccessTokenRespDTO> removeAccessToken(String accessToken) {
        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.removeAccessToken(accessToken);
        return success(OAuth2TokenConvert.INSTANCE.convert2(accessTokenDO));
    }

    @Override
    public CommonResult<OAuth2AccessTokenRespDTO> refreshAccessToken(String refreshToken, String clientId) {
        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.refreshAccessToken(refreshToken, clientId);
        return success(OAuth2TokenConvert.INSTANCE.convert2(accessTokenDO));
    }

}
