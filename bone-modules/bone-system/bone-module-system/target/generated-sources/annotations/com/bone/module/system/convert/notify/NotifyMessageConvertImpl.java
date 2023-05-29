package com.bone.module.system.convert.notify;

import com.bone.base.core.pojo.PageResult;
import com.bone.module.system.controller.admin.notify.vo.message.NotifyMessageRespVO;
import com.bone.module.system.dal.dataobject.notify.NotifyMessageDO;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:15+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class NotifyMessageConvertImpl implements NotifyMessageConvert {

    @Override
    public NotifyMessageRespVO convert(NotifyMessageDO bean) {
        if ( bean == null ) {
            return null;
        }

        NotifyMessageRespVO notifyMessageRespVO = new NotifyMessageRespVO();

        notifyMessageRespVO.setUserId( bean.getUserId() );
        if ( bean.getUserType() != null ) {
            notifyMessageRespVO.setUserType( bean.getUserType().byteValue() );
        }
        notifyMessageRespVO.setTemplateId( bean.getTemplateId() );
        notifyMessageRespVO.setTemplateCode( bean.getTemplateCode() );
        notifyMessageRespVO.setTemplateNickname( bean.getTemplateNickname() );
        notifyMessageRespVO.setTemplateContent( bean.getTemplateContent() );
        notifyMessageRespVO.setTemplateType( bean.getTemplateType() );
        Map<String, Object> map = bean.getTemplateParams();
        if ( map != null ) {
            notifyMessageRespVO.setTemplateParams( new LinkedHashMap<String, Object>( map ) );
        }
        notifyMessageRespVO.setReadStatus( bean.getReadStatus() );
        notifyMessageRespVO.setReadTime( bean.getReadTime() );
        notifyMessageRespVO.setId( bean.getId() );
        if ( bean.getCreateTime() != null ) {
            notifyMessageRespVO.setCreateTime( Date.from( bean.getCreateTime().toInstant( ZoneOffset.UTC ) ) );
        }

        return notifyMessageRespVO;
    }

    @Override
    public List<NotifyMessageRespVO> convertList(List<NotifyMessageDO> list) {
        if ( list == null ) {
            return null;
        }

        List<NotifyMessageRespVO> list1 = new ArrayList<NotifyMessageRespVO>( list.size() );
        for ( NotifyMessageDO notifyMessageDO : list ) {
            list1.add( convert( notifyMessageDO ) );
        }

        return list1;
    }

    @Override
    public PageResult<NotifyMessageRespVO> convertPage(PageResult<NotifyMessageDO> page) {
        if ( page == null ) {
            return null;
        }

        PageResult<NotifyMessageRespVO> pageResult = new PageResult<NotifyMessageRespVO>();

        pageResult.setList( convertList( page.getList() ) );
        pageResult.setTotal( page.getTotal() );

        return pageResult;
    }
}
