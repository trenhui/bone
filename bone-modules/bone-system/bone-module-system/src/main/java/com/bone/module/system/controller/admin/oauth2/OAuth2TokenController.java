package com.bone.module.system.controller.admin.oauth2;

import com.bone.base.core.pojo.CommonResult;
import com.bone.base.core.pojo.PageResult;
import com.bone.module.system.controller.admin.oauth2.vo.token.OAuth2AccessTokenPageReqVO;
import com.bone.module.system.controller.admin.oauth2.vo.token.OAuth2AccessTokenRespVO;
import com.bone.module.system.convert.auth.OAuth2TokenConvert;
import com.bone.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import com.bone.module.system.enums.logger.LoginLogTypeEnum;
import com.bone.module.system.service.auth.AdminAuthService;
import com.bone.module.system.service.oauth2.OAuth2TokenService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import static com.bone.base.core.pojo.CommonResult.success;

@Api(tags = "管理后台 - OAuth2.0 令牌")
@RestController
@RequestMapping("/system/oauth2-token")
public class OAuth2TokenController {

    @Resource
    private OAuth2TokenService oauth2TokenService;
    @Resource
    private AdminAuthService authService;

    @GetMapping("/page")
    @ApiOperation(value = "获得访问令牌分页", notes = "只返回有效期内的")
    @PreAuthorize("@ss.hasPermission('system:oauth2-token:page')")
    public CommonResult<PageResult<OAuth2AccessTokenRespVO>> getAccessTokenPage(@Valid OAuth2AccessTokenPageReqVO reqVO) {
        PageResult<OAuth2AccessTokenDO> pageResult = oauth2TokenService.getAccessTokenPage(reqVO);
        return success(OAuth2TokenConvert.INSTANCE.convert(pageResult));
    }

    @DeleteMapping("/delete")
    @ApiOperation("删除访问令牌")
    @ApiImplicitParam(name = "accessToken", value = "访问令牌", required = true, dataTypeClass = String.class, example = "tudou")
    @PreAuthorize("@ss.hasPermission('system:oauth2-token:delete')")
    public CommonResult<Boolean> deleteAccessToken(@RequestParam("accessToken") String accessToken) {
        authService.logout(accessToken, LoginLogTypeEnum.LOGOUT_DELETE.getType());
        return success(true);
    }

}
