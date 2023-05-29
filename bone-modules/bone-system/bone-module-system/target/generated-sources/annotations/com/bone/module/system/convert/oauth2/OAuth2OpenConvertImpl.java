package com.bone.module.system.convert.oauth2;

import com.bone.module.system.controller.admin.oauth2.vo.open.OAuth2OpenAccessTokenRespVO;
import com.bone.module.system.controller.admin.oauth2.vo.open.OAuth2OpenCheckTokenRespVO;
import com.bone.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:15+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class OAuth2OpenConvertImpl implements OAuth2OpenConvert {

    @Override
    public OAuth2OpenAccessTokenRespVO convert0(OAuth2AccessTokenDO bean) {
        if ( bean == null ) {
            return null;
        }

        OAuth2OpenAccessTokenRespVO oAuth2OpenAccessTokenRespVO = new OAuth2OpenAccessTokenRespVO();

        oAuth2OpenAccessTokenRespVO.setAccessToken( bean.getAccessToken() );
        oAuth2OpenAccessTokenRespVO.setRefreshToken( bean.getRefreshToken() );

        return oAuth2OpenAccessTokenRespVO;
    }

    @Override
    public OAuth2OpenCheckTokenRespVO convert3(OAuth2AccessTokenDO bean) {
        if ( bean == null ) {
            return null;
        }

        OAuth2OpenCheckTokenRespVO oAuth2OpenCheckTokenRespVO = new OAuth2OpenCheckTokenRespVO();

        oAuth2OpenCheckTokenRespVO.setUserId( bean.getUserId() );
        oAuth2OpenCheckTokenRespVO.setUserType( bean.getUserType() );
        oAuth2OpenCheckTokenRespVO.setTenantId( bean.getTenantId() );
        oAuth2OpenCheckTokenRespVO.setClientId( bean.getClientId() );
        List<String> list = bean.getScopes();
        if ( list != null ) {
            oAuth2OpenCheckTokenRespVO.setScopes( new ArrayList<String>( list ) );
        }
        oAuth2OpenCheckTokenRespVO.setAccessToken( bean.getAccessToken() );

        return oAuth2OpenCheckTokenRespVO;
    }
}
