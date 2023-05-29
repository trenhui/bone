package com.bone.module.system.convert.auth;

import com.bone.base.core.pojo.PageResult;
import com.bone.module.system.api.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import com.bone.module.system.api.oauth2.dto.OAuth2AccessTokenRespDTO;
import com.bone.module.system.controller.admin.oauth2.vo.token.OAuth2AccessTokenRespVO;
import com.bone.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:15+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class OAuth2TokenConvertImpl implements OAuth2TokenConvert {

    @Override
    public OAuth2AccessTokenCheckRespDTO convert(OAuth2AccessTokenDO bean) {
        if ( bean == null ) {
            return null;
        }

        OAuth2AccessTokenCheckRespDTO oAuth2AccessTokenCheckRespDTO = new OAuth2AccessTokenCheckRespDTO();

        oAuth2AccessTokenCheckRespDTO.setUserId( bean.getUserId() );
        oAuth2AccessTokenCheckRespDTO.setUserType( bean.getUserType() );
        oAuth2AccessTokenCheckRespDTO.setTenantId( bean.getTenantId() );
        List<String> list = bean.getScopes();
        if ( list != null ) {
            oAuth2AccessTokenCheckRespDTO.setScopes( new ArrayList<String>( list ) );
        }

        return oAuth2AccessTokenCheckRespDTO;
    }

    @Override
    public PageResult<OAuth2AccessTokenRespVO> convert(PageResult<OAuth2AccessTokenDO> page) {
        if ( page == null ) {
            return null;
        }

        PageResult<OAuth2AccessTokenRespVO> pageResult = new PageResult<OAuth2AccessTokenRespVO>();

        pageResult.setList( oAuth2AccessTokenDOListToOAuth2AccessTokenRespVOList( page.getList() ) );
        pageResult.setTotal( page.getTotal() );

        return pageResult;
    }

    @Override
    public OAuth2AccessTokenRespDTO convert2(OAuth2AccessTokenDO bean) {
        if ( bean == null ) {
            return null;
        }

        OAuth2AccessTokenRespDTO oAuth2AccessTokenRespDTO = new OAuth2AccessTokenRespDTO();

        oAuth2AccessTokenRespDTO.setAccessToken( bean.getAccessToken() );
        oAuth2AccessTokenRespDTO.setRefreshToken( bean.getRefreshToken() );
        oAuth2AccessTokenRespDTO.setUserId( bean.getUserId() );
        oAuth2AccessTokenRespDTO.setUserType( bean.getUserType() );
        oAuth2AccessTokenRespDTO.setExpiresTime( bean.getExpiresTime() );

        return oAuth2AccessTokenRespDTO;
    }

    protected OAuth2AccessTokenRespVO oAuth2AccessTokenDOToOAuth2AccessTokenRespVO(OAuth2AccessTokenDO oAuth2AccessTokenDO) {
        if ( oAuth2AccessTokenDO == null ) {
            return null;
        }

        OAuth2AccessTokenRespVO oAuth2AccessTokenRespVO = new OAuth2AccessTokenRespVO();

        oAuth2AccessTokenRespVO.setId( oAuth2AccessTokenDO.getId() );
        oAuth2AccessTokenRespVO.setAccessToken( oAuth2AccessTokenDO.getAccessToken() );
        oAuth2AccessTokenRespVO.setRefreshToken( oAuth2AccessTokenDO.getRefreshToken() );
        oAuth2AccessTokenRespVO.setUserId( oAuth2AccessTokenDO.getUserId() );
        oAuth2AccessTokenRespVO.setUserType( oAuth2AccessTokenDO.getUserType() );
        oAuth2AccessTokenRespVO.setClientId( oAuth2AccessTokenDO.getClientId() );
        oAuth2AccessTokenRespVO.setCreateTime( oAuth2AccessTokenDO.getCreateTime() );
        oAuth2AccessTokenRespVO.setExpiresTime( oAuth2AccessTokenDO.getExpiresTime() );

        return oAuth2AccessTokenRespVO;
    }

    protected List<OAuth2AccessTokenRespVO> oAuth2AccessTokenDOListToOAuth2AccessTokenRespVOList(List<OAuth2AccessTokenDO> list) {
        if ( list == null ) {
            return null;
        }

        List<OAuth2AccessTokenRespVO> list1 = new ArrayList<OAuth2AccessTokenRespVO>( list.size() );
        for ( OAuth2AccessTokenDO oAuth2AccessTokenDO : list ) {
            list1.add( oAuth2AccessTokenDOToOAuth2AccessTokenRespVO( oAuth2AccessTokenDO ) );
        }

        return list1;
    }
}
