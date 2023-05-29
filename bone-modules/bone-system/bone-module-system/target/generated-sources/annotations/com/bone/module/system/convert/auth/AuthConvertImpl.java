package com.bone.module.system.convert.auth;

import com.bone.module.system.api.sms.dto.code.SmsCodeSendReqDTO;
import com.bone.module.system.api.sms.dto.code.SmsCodeUseReqDTO;
import com.bone.module.system.api.social.dto.SocialUserBindReqDTO;
import com.bone.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import com.bone.module.system.controller.admin.auth.vo.AuthMenuRespVO;
import com.bone.module.system.controller.admin.auth.vo.AuthSmsLoginReqVO;
import com.bone.module.system.controller.admin.auth.vo.AuthSmsSendReqVO;
import com.bone.module.system.controller.admin.auth.vo.AuthSocialBindLoginReqVO;
import com.bone.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import com.bone.module.system.dal.dataobject.permission.MenuDO;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:15+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class AuthConvertImpl implements AuthConvert {

    @Override
    public AuthLoginRespVO convert(OAuth2AccessTokenDO bean) {
        if ( bean == null ) {
            return null;
        }

        AuthLoginRespVO.AuthLoginRespVOBuilder authLoginRespVO = AuthLoginRespVO.builder();

        authLoginRespVO.userId( bean.getUserId() );
        authLoginRespVO.accessToken( bean.getAccessToken() );
        authLoginRespVO.refreshToken( bean.getRefreshToken() );
        authLoginRespVO.expiresTime( bean.getExpiresTime() );
        authLoginRespVO.tenantId( bean.getTenantId() );

        return authLoginRespVO.build();
    }

    @Override
    public AuthMenuRespVO convertTreeNode(MenuDO menu) {
        if ( menu == null ) {
            return null;
        }

        AuthMenuRespVO.AuthMenuRespVOBuilder authMenuRespVO = AuthMenuRespVO.builder();

        authMenuRespVO.id( menu.getId() );
        authMenuRespVO.parentId( menu.getParentId() );
        authMenuRespVO.name( menu.getName() );
        authMenuRespVO.path( menu.getPath() );
        authMenuRespVO.component( menu.getComponent() );
        authMenuRespVO.icon( menu.getIcon() );
        authMenuRespVO.visible( menu.getVisible() );
        authMenuRespVO.keepAlive( menu.getKeepAlive() );

        return authMenuRespVO.build();
    }

    @Override
    public SocialUserBindReqDTO convert(Long userId, Integer userType, AuthSocialBindLoginReqVO reqVO) {
        if ( userId == null && userType == null && reqVO == null ) {
            return null;
        }

        SocialUserBindReqDTO socialUserBindReqDTO = new SocialUserBindReqDTO();

        if ( reqVO != null ) {
            socialUserBindReqDTO.setType( reqVO.getType() );
            socialUserBindReqDTO.setCode( reqVO.getCode() );
            socialUserBindReqDTO.setState( reqVO.getState() );
        }
        socialUserBindReqDTO.setUserId( userId );
        socialUserBindReqDTO.setUserType( userType );

        return socialUserBindReqDTO;
    }

    @Override
    public SmsCodeSendReqDTO convert(AuthSmsSendReqVO reqVO) {
        if ( reqVO == null ) {
            return null;
        }

        SmsCodeSendReqDTO smsCodeSendReqDTO = new SmsCodeSendReqDTO();

        smsCodeSendReqDTO.setMobile( reqVO.getMobile() );
        smsCodeSendReqDTO.setScene( reqVO.getScene() );

        return smsCodeSendReqDTO;
    }

    @Override
    public SmsCodeUseReqDTO convert(AuthSmsLoginReqVO reqVO, Integer scene, String usedIp) {
        if ( reqVO == null && scene == null && usedIp == null ) {
            return null;
        }

        SmsCodeUseReqDTO smsCodeUseReqDTO = new SmsCodeUseReqDTO();

        if ( reqVO != null ) {
            smsCodeUseReqDTO.setMobile( reqVO.getMobile() );
            smsCodeUseReqDTO.setCode( reqVO.getCode() );
        }
        smsCodeUseReqDTO.setScene( scene );
        smsCodeUseReqDTO.setUsedIp( usedIp );

        return smsCodeUseReqDTO;
    }
}
