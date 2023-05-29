package com.bone.module.system.convert.auth;

import com.bone.base.core.pojo.PageResult;
import com.bone.module.system.controller.admin.oauth2.vo.client.OAuth2ClientCreateReqVO;
import com.bone.module.system.controller.admin.oauth2.vo.client.OAuth2ClientRespVO;
import com.bone.module.system.controller.admin.oauth2.vo.client.OAuth2ClientUpdateReqVO;
import com.bone.module.system.dal.dataobject.oauth2.OAuth2ClientDO;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:15+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class OAuth2ClientConvertImpl implements OAuth2ClientConvert {

    @Override
    public OAuth2ClientDO convert(OAuth2ClientCreateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        OAuth2ClientDO oAuth2ClientDO = new OAuth2ClientDO();

        oAuth2ClientDO.setClientId( bean.getClientId() );
        oAuth2ClientDO.setSecret( bean.getSecret() );
        oAuth2ClientDO.setName( bean.getName() );
        oAuth2ClientDO.setLogo( bean.getLogo() );
        oAuth2ClientDO.setDescription( bean.getDescription() );
        oAuth2ClientDO.setStatus( bean.getStatus() );
        oAuth2ClientDO.setAccessTokenValiditySeconds( bean.getAccessTokenValiditySeconds() );
        oAuth2ClientDO.setRefreshTokenValiditySeconds( bean.getRefreshTokenValiditySeconds() );
        List<String> list = bean.getRedirectUris();
        if ( list != null ) {
            oAuth2ClientDO.setRedirectUris( new ArrayList<String>( list ) );
        }
        List<String> list1 = bean.getAuthorizedGrantTypes();
        if ( list1 != null ) {
            oAuth2ClientDO.setAuthorizedGrantTypes( new ArrayList<String>( list1 ) );
        }
        List<String> list2 = bean.getScopes();
        if ( list2 != null ) {
            oAuth2ClientDO.setScopes( new ArrayList<String>( list2 ) );
        }
        List<String> list3 = bean.getAutoApproveScopes();
        if ( list3 != null ) {
            oAuth2ClientDO.setAutoApproveScopes( new ArrayList<String>( list3 ) );
        }
        List<String> list4 = bean.getAuthorities();
        if ( list4 != null ) {
            oAuth2ClientDO.setAuthorities( new ArrayList<String>( list4 ) );
        }
        List<String> list5 = bean.getResourceIds();
        if ( list5 != null ) {
            oAuth2ClientDO.setResourceIds( new ArrayList<String>( list5 ) );
        }
        oAuth2ClientDO.setAdditionalInformation( bean.getAdditionalInformation() );

        return oAuth2ClientDO;
    }

    @Override
    public OAuth2ClientDO convert(OAuth2ClientUpdateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        OAuth2ClientDO oAuth2ClientDO = new OAuth2ClientDO();

        oAuth2ClientDO.setId( bean.getId() );
        oAuth2ClientDO.setClientId( bean.getClientId() );
        oAuth2ClientDO.setSecret( bean.getSecret() );
        oAuth2ClientDO.setName( bean.getName() );
        oAuth2ClientDO.setLogo( bean.getLogo() );
        oAuth2ClientDO.setDescription( bean.getDescription() );
        oAuth2ClientDO.setStatus( bean.getStatus() );
        oAuth2ClientDO.setAccessTokenValiditySeconds( bean.getAccessTokenValiditySeconds() );
        oAuth2ClientDO.setRefreshTokenValiditySeconds( bean.getRefreshTokenValiditySeconds() );
        List<String> list = bean.getRedirectUris();
        if ( list != null ) {
            oAuth2ClientDO.setRedirectUris( new ArrayList<String>( list ) );
        }
        List<String> list1 = bean.getAuthorizedGrantTypes();
        if ( list1 != null ) {
            oAuth2ClientDO.setAuthorizedGrantTypes( new ArrayList<String>( list1 ) );
        }
        List<String> list2 = bean.getScopes();
        if ( list2 != null ) {
            oAuth2ClientDO.setScopes( new ArrayList<String>( list2 ) );
        }
        List<String> list3 = bean.getAutoApproveScopes();
        if ( list3 != null ) {
            oAuth2ClientDO.setAutoApproveScopes( new ArrayList<String>( list3 ) );
        }
        List<String> list4 = bean.getAuthorities();
        if ( list4 != null ) {
            oAuth2ClientDO.setAuthorities( new ArrayList<String>( list4 ) );
        }
        List<String> list5 = bean.getResourceIds();
        if ( list5 != null ) {
            oAuth2ClientDO.setResourceIds( new ArrayList<String>( list5 ) );
        }
        oAuth2ClientDO.setAdditionalInformation( bean.getAdditionalInformation() );

        return oAuth2ClientDO;
    }

    @Override
    public OAuth2ClientRespVO convert(OAuth2ClientDO bean) {
        if ( bean == null ) {
            return null;
        }

        OAuth2ClientRespVO oAuth2ClientRespVO = new OAuth2ClientRespVO();

        oAuth2ClientRespVO.setClientId( bean.getClientId() );
        oAuth2ClientRespVO.setSecret( bean.getSecret() );
        oAuth2ClientRespVO.setName( bean.getName() );
        oAuth2ClientRespVO.setLogo( bean.getLogo() );
        oAuth2ClientRespVO.setDescription( bean.getDescription() );
        oAuth2ClientRespVO.setStatus( bean.getStatus() );
        oAuth2ClientRespVO.setAccessTokenValiditySeconds( bean.getAccessTokenValiditySeconds() );
        oAuth2ClientRespVO.setRefreshTokenValiditySeconds( bean.getRefreshTokenValiditySeconds() );
        List<String> list = bean.getRedirectUris();
        if ( list != null ) {
            oAuth2ClientRespVO.setRedirectUris( new ArrayList<String>( list ) );
        }
        List<String> list1 = bean.getAuthorizedGrantTypes();
        if ( list1 != null ) {
            oAuth2ClientRespVO.setAuthorizedGrantTypes( new ArrayList<String>( list1 ) );
        }
        List<String> list2 = bean.getScopes();
        if ( list2 != null ) {
            oAuth2ClientRespVO.setScopes( new ArrayList<String>( list2 ) );
        }
        List<String> list3 = bean.getAutoApproveScopes();
        if ( list3 != null ) {
            oAuth2ClientRespVO.setAutoApproveScopes( new ArrayList<String>( list3 ) );
        }
        List<String> list4 = bean.getAuthorities();
        if ( list4 != null ) {
            oAuth2ClientRespVO.setAuthorities( new ArrayList<String>( list4 ) );
        }
        List<String> list5 = bean.getResourceIds();
        if ( list5 != null ) {
            oAuth2ClientRespVO.setResourceIds( new ArrayList<String>( list5 ) );
        }
        oAuth2ClientRespVO.setAdditionalInformation( bean.getAdditionalInformation() );
        oAuth2ClientRespVO.setId( bean.getId() );
        oAuth2ClientRespVO.setCreateTime( bean.getCreateTime() );

        return oAuth2ClientRespVO;
    }

    @Override
    public List<OAuth2ClientRespVO> convertList(List<OAuth2ClientDO> list) {
        if ( list == null ) {
            return null;
        }

        List<OAuth2ClientRespVO> list1 = new ArrayList<OAuth2ClientRespVO>( list.size() );
        for ( OAuth2ClientDO oAuth2ClientDO : list ) {
            list1.add( convert( oAuth2ClientDO ) );
        }

        return list1;
    }

    @Override
    public PageResult<OAuth2ClientRespVO> convertPage(PageResult<OAuth2ClientDO> page) {
        if ( page == null ) {
            return null;
        }

        PageResult<OAuth2ClientRespVO> pageResult = new PageResult<OAuth2ClientRespVO>();

        pageResult.setList( convertList( page.getList() ) );
        pageResult.setTotal( page.getTotal() );

        return pageResult;
    }
}
