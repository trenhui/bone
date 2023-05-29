package com.bone.module.report.convert.goview;

import com.bone.base.core.pojo.PageResult;
import com.bone.module.report.controller.admin.goview.vo.project.GoViewProjectCreateReqVO;
import com.bone.module.report.controller.admin.goview.vo.project.GoViewProjectRespVO;
import com.bone.module.report.controller.admin.goview.vo.project.GoViewProjectUpdateReqVO;
import com.bone.module.report.dal.dataobject.goview.GoViewProjectDO;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:45+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class GoViewProjectConvertImpl implements GoViewProjectConvert {

    @Override
    public GoViewProjectDO convert(GoViewProjectCreateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        GoViewProjectDO.GoViewProjectDOBuilder goViewProjectDO = GoViewProjectDO.builder();

        goViewProjectDO.name( bean.getName() );

        return goViewProjectDO.build();
    }

    @Override
    public GoViewProjectDO convert(GoViewProjectUpdateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        GoViewProjectDO.GoViewProjectDOBuilder goViewProjectDO = GoViewProjectDO.builder();

        goViewProjectDO.id( bean.getId() );
        goViewProjectDO.name( bean.getName() );
        goViewProjectDO.picUrl( bean.getPicUrl() );
        goViewProjectDO.content( bean.getContent() );
        goViewProjectDO.status( bean.getStatus() );
        goViewProjectDO.remark( bean.getRemark() );

        return goViewProjectDO.build();
    }

    @Override
    public GoViewProjectRespVO convert(GoViewProjectDO bean) {
        if ( bean == null ) {
            return null;
        }

        GoViewProjectRespVO goViewProjectRespVO = new GoViewProjectRespVO();

        goViewProjectRespVO.setId( bean.getId() );
        goViewProjectRespVO.setName( bean.getName() );
        goViewProjectRespVO.setStatus( bean.getStatus() );
        goViewProjectRespVO.setContent( bean.getContent() );
        goViewProjectRespVO.setPicUrl( bean.getPicUrl() );
        goViewProjectRespVO.setRemark( bean.getRemark() );
        goViewProjectRespVO.setCreator( bean.getCreator() );
        goViewProjectRespVO.setCreateTime( bean.getCreateTime() );

        return goViewProjectRespVO;
    }

    @Override
    public PageResult<GoViewProjectRespVO> convertPage(PageResult<GoViewProjectDO> page) {
        if ( page == null ) {
            return null;
        }

        PageResult<GoViewProjectRespVO> pageResult = new PageResult<GoViewProjectRespVO>();

        pageResult.setList( goViewProjectDOListToGoViewProjectRespVOList( page.getList() ) );
        pageResult.setTotal( page.getTotal() );

        return pageResult;
    }

    protected List<GoViewProjectRespVO> goViewProjectDOListToGoViewProjectRespVOList(List<GoViewProjectDO> list) {
        if ( list == null ) {
            return null;
        }

        List<GoViewProjectRespVO> list1 = new ArrayList<GoViewProjectRespVO>( list.size() );
        for ( GoViewProjectDO goViewProjectDO : list ) {
            list1.add( convert( goViewProjectDO ) );
        }

        return list1;
    }
}
