package com.bone.module.system.convert.sms;

import com.bone.base.core.pojo.PageResult;
import com.bone.base.sms.core.property.SmsChannelProperties;
import com.bone.module.system.controller.admin.sms.vo.channel.SmsChannelCreateReqVO;
import com.bone.module.system.controller.admin.sms.vo.channel.SmsChannelRespVO;
import com.bone.module.system.controller.admin.sms.vo.channel.SmsChannelSimpleRespVO;
import com.bone.module.system.controller.admin.sms.vo.channel.SmsChannelUpdateReqVO;
import com.bone.module.system.dal.dataobject.sms.SmsChannelDO;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:16+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class SmsChannelConvertImpl implements SmsChannelConvert {

    @Override
    public SmsChannelDO convert(SmsChannelCreateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        SmsChannelDO smsChannelDO = new SmsChannelDO();

        smsChannelDO.setSignature( bean.getSignature() );
        smsChannelDO.setCode( bean.getCode() );
        smsChannelDO.setStatus( bean.getStatus() );
        smsChannelDO.setRemark( bean.getRemark() );
        smsChannelDO.setApiKey( bean.getApiKey() );
        smsChannelDO.setApiSecret( bean.getApiSecret() );
        smsChannelDO.setCallbackUrl( bean.getCallbackUrl() );

        return smsChannelDO;
    }

    @Override
    public SmsChannelDO convert(SmsChannelUpdateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        SmsChannelDO smsChannelDO = new SmsChannelDO();

        smsChannelDO.setId( bean.getId() );
        smsChannelDO.setSignature( bean.getSignature() );
        smsChannelDO.setStatus( bean.getStatus() );
        smsChannelDO.setRemark( bean.getRemark() );
        smsChannelDO.setApiKey( bean.getApiKey() );
        smsChannelDO.setApiSecret( bean.getApiSecret() );
        smsChannelDO.setCallbackUrl( bean.getCallbackUrl() );

        return smsChannelDO;
    }

    @Override
    public SmsChannelRespVO convert(SmsChannelDO bean) {
        if ( bean == null ) {
            return null;
        }

        SmsChannelRespVO smsChannelRespVO = new SmsChannelRespVO();

        smsChannelRespVO.setSignature( bean.getSignature() );
        smsChannelRespVO.setStatus( bean.getStatus() );
        smsChannelRespVO.setRemark( bean.getRemark() );
        smsChannelRespVO.setApiKey( bean.getApiKey() );
        smsChannelRespVO.setApiSecret( bean.getApiSecret() );
        smsChannelRespVO.setCallbackUrl( bean.getCallbackUrl() );
        smsChannelRespVO.setId( bean.getId() );
        smsChannelRespVO.setCode( bean.getCode() );
        smsChannelRespVO.setCreateTime( bean.getCreateTime() );

        return smsChannelRespVO;
    }

    @Override
    public List<SmsChannelRespVO> convertList(List<SmsChannelDO> list) {
        if ( list == null ) {
            return null;
        }

        List<SmsChannelRespVO> list1 = new ArrayList<SmsChannelRespVO>( list.size() );
        for ( SmsChannelDO smsChannelDO : list ) {
            list1.add( convert( smsChannelDO ) );
        }

        return list1;
    }

    @Override
    public PageResult<SmsChannelRespVO> convertPage(PageResult<SmsChannelDO> page) {
        if ( page == null ) {
            return null;
        }

        PageResult<SmsChannelRespVO> pageResult = new PageResult<SmsChannelRespVO>();

        pageResult.setList( convertList( page.getList() ) );
        pageResult.setTotal( page.getTotal() );

        return pageResult;
    }

    @Override
    public List<SmsChannelProperties> convertList02(List<SmsChannelDO> list) {
        if ( list == null ) {
            return null;
        }

        List<SmsChannelProperties> list1 = new ArrayList<SmsChannelProperties>( list.size() );
        for ( SmsChannelDO smsChannelDO : list ) {
            list1.add( smsChannelDOToSmsChannelProperties( smsChannelDO ) );
        }

        return list1;
    }

    @Override
    public List<SmsChannelSimpleRespVO> convertList03(List<SmsChannelDO> list) {
        if ( list == null ) {
            return null;
        }

        List<SmsChannelSimpleRespVO> list1 = new ArrayList<SmsChannelSimpleRespVO>( list.size() );
        for ( SmsChannelDO smsChannelDO : list ) {
            list1.add( smsChannelDOToSmsChannelSimpleRespVO( smsChannelDO ) );
        }

        return list1;
    }

    protected SmsChannelProperties smsChannelDOToSmsChannelProperties(SmsChannelDO smsChannelDO) {
        if ( smsChannelDO == null ) {
            return null;
        }

        SmsChannelProperties smsChannelProperties = new SmsChannelProperties();

        smsChannelProperties.setId( smsChannelDO.getId() );
        smsChannelProperties.setSignature( smsChannelDO.getSignature() );
        smsChannelProperties.setCode( smsChannelDO.getCode() );
        smsChannelProperties.setApiKey( smsChannelDO.getApiKey() );
        smsChannelProperties.setApiSecret( smsChannelDO.getApiSecret() );
        smsChannelProperties.setCallbackUrl( smsChannelDO.getCallbackUrl() );

        return smsChannelProperties;
    }

    protected SmsChannelSimpleRespVO smsChannelDOToSmsChannelSimpleRespVO(SmsChannelDO smsChannelDO) {
        if ( smsChannelDO == null ) {
            return null;
        }

        SmsChannelSimpleRespVO smsChannelSimpleRespVO = new SmsChannelSimpleRespVO();

        smsChannelSimpleRespVO.setId( smsChannelDO.getId() );
        smsChannelSimpleRespVO.setSignature( smsChannelDO.getSignature() );
        smsChannelSimpleRespVO.setCode( smsChannelDO.getCode() );

        return smsChannelSimpleRespVO;
    }
}
