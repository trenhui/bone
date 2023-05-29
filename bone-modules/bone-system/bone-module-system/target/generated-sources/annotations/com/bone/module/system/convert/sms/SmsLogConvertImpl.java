package com.bone.module.system.convert.sms;

import com.bone.base.core.pojo.PageResult;
import com.bone.module.system.controller.admin.sms.vo.log.SmsLogExcelVO;
import com.bone.module.system.controller.admin.sms.vo.log.SmsLogRespVO;
import com.bone.module.system.dal.dataobject.sms.SmsLogDO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:15+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class SmsLogConvertImpl implements SmsLogConvert {

    @Override
    public SmsLogRespVO convert(SmsLogDO bean) {
        if ( bean == null ) {
            return null;
        }

        SmsLogRespVO smsLogRespVO = new SmsLogRespVO();

        smsLogRespVO.setId( bean.getId() );
        smsLogRespVO.setChannelId( bean.getChannelId() );
        smsLogRespVO.setChannelCode( bean.getChannelCode() );
        smsLogRespVO.setTemplateId( bean.getTemplateId() );
        smsLogRespVO.setTemplateCode( bean.getTemplateCode() );
        smsLogRespVO.setTemplateType( bean.getTemplateType() );
        smsLogRespVO.setTemplateContent( bean.getTemplateContent() );
        Map<String, Object> map = bean.getTemplateParams();
        if ( map != null ) {
            smsLogRespVO.setTemplateParams( new LinkedHashMap<String, Object>( map ) );
        }
        smsLogRespVO.setApiTemplateId( bean.getApiTemplateId() );
        smsLogRespVO.setMobile( bean.getMobile() );
        smsLogRespVO.setUserId( bean.getUserId() );
        smsLogRespVO.setUserType( bean.getUserType() );
        smsLogRespVO.setSendStatus( bean.getSendStatus() );
        smsLogRespVO.setSendTime( bean.getSendTime() );
        smsLogRespVO.setSendCode( bean.getSendCode() );
        smsLogRespVO.setSendMsg( bean.getSendMsg() );
        smsLogRespVO.setApiSendCode( bean.getApiSendCode() );
        smsLogRespVO.setApiSendMsg( bean.getApiSendMsg() );
        smsLogRespVO.setApiRequestId( bean.getApiRequestId() );
        smsLogRespVO.setApiSerialNo( bean.getApiSerialNo() );
        smsLogRespVO.setReceiveStatus( bean.getReceiveStatus() );
        smsLogRespVO.setReceiveTime( bean.getReceiveTime() );
        smsLogRespVO.setApiReceiveCode( bean.getApiReceiveCode() );
        smsLogRespVO.setApiReceiveMsg( bean.getApiReceiveMsg() );
        smsLogRespVO.setCreateTime( bean.getCreateTime() );

        return smsLogRespVO;
    }

    @Override
    public List<SmsLogRespVO> convertList(List<SmsLogDO> list) {
        if ( list == null ) {
            return null;
        }

        List<SmsLogRespVO> list1 = new ArrayList<SmsLogRespVO>( list.size() );
        for ( SmsLogDO smsLogDO : list ) {
            list1.add( convert( smsLogDO ) );
        }

        return list1;
    }

    @Override
    public PageResult<SmsLogRespVO> convertPage(PageResult<SmsLogDO> page) {
        if ( page == null ) {
            return null;
        }

        PageResult<SmsLogRespVO> pageResult = new PageResult<SmsLogRespVO>();

        pageResult.setList( convertList( page.getList() ) );
        pageResult.setTotal( page.getTotal() );

        return pageResult;
    }

    @Override
    public List<SmsLogExcelVO> convertList02(List<SmsLogDO> list) {
        if ( list == null ) {
            return null;
        }

        List<SmsLogExcelVO> list1 = new ArrayList<SmsLogExcelVO>( list.size() );
        for ( SmsLogDO smsLogDO : list ) {
            list1.add( smsLogDOToSmsLogExcelVO( smsLogDO ) );
        }

        return list1;
    }

    protected SmsLogExcelVO smsLogDOToSmsLogExcelVO(SmsLogDO smsLogDO) {
        if ( smsLogDO == null ) {
            return null;
        }

        SmsLogExcelVO smsLogExcelVO = new SmsLogExcelVO();

        smsLogExcelVO.setId( smsLogDO.getId() );
        smsLogExcelVO.setChannelId( smsLogDO.getChannelId() );
        smsLogExcelVO.setChannelCode( smsLogDO.getChannelCode() );
        smsLogExcelVO.setTemplateId( smsLogDO.getTemplateId() );
        smsLogExcelVO.setTemplateCode( smsLogDO.getTemplateCode() );
        smsLogExcelVO.setTemplateType( smsLogDO.getTemplateType() );
        smsLogExcelVO.setTemplateContent( smsLogDO.getTemplateContent() );
        Map<String, Object> map = smsLogDO.getTemplateParams();
        if ( map != null ) {
            smsLogExcelVO.setTemplateParams( new LinkedHashMap<String, Object>( map ) );
        }
        smsLogExcelVO.setApiTemplateId( smsLogDO.getApiTemplateId() );
        smsLogExcelVO.setMobile( smsLogDO.getMobile() );
        smsLogExcelVO.setUserId( smsLogDO.getUserId() );
        smsLogExcelVO.setUserType( smsLogDO.getUserType() );
        smsLogExcelVO.setSendStatus( smsLogDO.getSendStatus() );
        smsLogExcelVO.setSendTime( smsLogDO.getSendTime() );
        smsLogExcelVO.setSendCode( smsLogDO.getSendCode() );
        smsLogExcelVO.setSendMsg( smsLogDO.getSendMsg() );
        smsLogExcelVO.setApiSendCode( smsLogDO.getApiSendCode() );
        smsLogExcelVO.setApiSendMsg( smsLogDO.getApiSendMsg() );
        smsLogExcelVO.setApiRequestId( smsLogDO.getApiRequestId() );
        smsLogExcelVO.setApiSerialNo( smsLogDO.getApiSerialNo() );
        smsLogExcelVO.setReceiveStatus( smsLogDO.getReceiveStatus() );
        smsLogExcelVO.setReceiveTime( smsLogDO.getReceiveTime() );
        smsLogExcelVO.setApiReceiveCode( smsLogDO.getApiReceiveCode() );
        smsLogExcelVO.setApiReceiveMsg( smsLogDO.getApiReceiveMsg() );
        smsLogExcelVO.setCreateTime( smsLogDO.getCreateTime() );

        return smsLogExcelVO;
    }
}
