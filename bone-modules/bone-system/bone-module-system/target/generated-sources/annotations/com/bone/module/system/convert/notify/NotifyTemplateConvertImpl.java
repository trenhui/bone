package com.bone.module.system.convert.notify;

import com.bone.base.core.pojo.PageResult;
import com.bone.module.system.controller.admin.notify.vo.template.NotifyTemplateCreateReqVO;
import com.bone.module.system.controller.admin.notify.vo.template.NotifyTemplateRespVO;
import com.bone.module.system.controller.admin.notify.vo.template.NotifyTemplateUpdateReqVO;
import com.bone.module.system.dal.dataobject.notify.NotifyTemplateDO;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:16+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class NotifyTemplateConvertImpl implements NotifyTemplateConvert {

    @Override
    public NotifyTemplateDO convert(NotifyTemplateCreateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        NotifyTemplateDO.NotifyTemplateDOBuilder notifyTemplateDO = NotifyTemplateDO.builder();

        notifyTemplateDO.name( bean.getName() );
        notifyTemplateDO.code( bean.getCode() );
        notifyTemplateDO.type( bean.getType() );
        notifyTemplateDO.nickname( bean.getNickname() );
        notifyTemplateDO.content( bean.getContent() );
        notifyTemplateDO.status( bean.getStatus() );
        notifyTemplateDO.remark( bean.getRemark() );

        return notifyTemplateDO.build();
    }

    @Override
    public NotifyTemplateDO convert(NotifyTemplateUpdateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        NotifyTemplateDO.NotifyTemplateDOBuilder notifyTemplateDO = NotifyTemplateDO.builder();

        notifyTemplateDO.id( bean.getId() );
        notifyTemplateDO.name( bean.getName() );
        notifyTemplateDO.code( bean.getCode() );
        notifyTemplateDO.type( bean.getType() );
        notifyTemplateDO.nickname( bean.getNickname() );
        notifyTemplateDO.content( bean.getContent() );
        notifyTemplateDO.status( bean.getStatus() );
        notifyTemplateDO.remark( bean.getRemark() );

        return notifyTemplateDO.build();
    }

    @Override
    public NotifyTemplateRespVO convert(NotifyTemplateDO bean) {
        if ( bean == null ) {
            return null;
        }

        NotifyTemplateRespVO notifyTemplateRespVO = new NotifyTemplateRespVO();

        notifyTemplateRespVO.setName( bean.getName() );
        notifyTemplateRespVO.setCode( bean.getCode() );
        notifyTemplateRespVO.setType( bean.getType() );
        notifyTemplateRespVO.setNickname( bean.getNickname() );
        notifyTemplateRespVO.setContent( bean.getContent() );
        notifyTemplateRespVO.setStatus( bean.getStatus() );
        notifyTemplateRespVO.setRemark( bean.getRemark() );
        notifyTemplateRespVO.setId( bean.getId() );
        List<String> list = bean.getParams();
        if ( list != null ) {
            notifyTemplateRespVO.setParams( new ArrayList<String>( list ) );
        }
        if ( bean.getCreateTime() != null ) {
            notifyTemplateRespVO.setCreateTime( Date.from( bean.getCreateTime().toInstant( ZoneOffset.UTC ) ) );
        }

        return notifyTemplateRespVO;
    }

    @Override
    public List<NotifyTemplateRespVO> convertList(List<NotifyTemplateDO> list) {
        if ( list == null ) {
            return null;
        }

        List<NotifyTemplateRespVO> list1 = new ArrayList<NotifyTemplateRespVO>( list.size() );
        for ( NotifyTemplateDO notifyTemplateDO : list ) {
            list1.add( convert( notifyTemplateDO ) );
        }

        return list1;
    }

    @Override
    public PageResult<NotifyTemplateRespVO> convertPage(PageResult<NotifyTemplateDO> page) {
        if ( page == null ) {
            return null;
        }

        PageResult<NotifyTemplateRespVO> pageResult = new PageResult<NotifyTemplateRespVO>();

        pageResult.setList( convertList( page.getList() ) );
        pageResult.setTotal( page.getTotal() );

        return pageResult;
    }
}
