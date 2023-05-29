package com.bone.module.bpm.convert.definition;

import com.bone.base.core.pojo.PageResult;
import com.bone.module.bpm.controller.admin.definition.vo.group.BpmUserGroupCreateReqVO;
import com.bone.module.bpm.controller.admin.definition.vo.group.BpmUserGroupRespVO;
import com.bone.module.bpm.controller.admin.definition.vo.group.BpmUserGroupUpdateReqVO;
import com.bone.module.bpm.dal.dataobject.definition.BpmUserGroupDO;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:37+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class BpmUserGroupConvertImpl implements BpmUserGroupConvert {

    @Override
    public BpmUserGroupDO convert(BpmUserGroupCreateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        BpmUserGroupDO.BpmUserGroupDOBuilder bpmUserGroupDO = BpmUserGroupDO.builder();

        bpmUserGroupDO.name( bean.getName() );
        bpmUserGroupDO.description( bean.getDescription() );
        bpmUserGroupDO.status( bean.getStatus() );
        Set<Long> set = bean.getMemberUserIds();
        if ( set != null ) {
            bpmUserGroupDO.memberUserIds( new LinkedHashSet<Long>( set ) );
        }

        return bpmUserGroupDO.build();
    }

    @Override
    public BpmUserGroupDO convert(BpmUserGroupUpdateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        BpmUserGroupDO.BpmUserGroupDOBuilder bpmUserGroupDO = BpmUserGroupDO.builder();

        bpmUserGroupDO.id( bean.getId() );
        bpmUserGroupDO.name( bean.getName() );
        bpmUserGroupDO.description( bean.getDescription() );
        bpmUserGroupDO.status( bean.getStatus() );
        Set<Long> set = bean.getMemberUserIds();
        if ( set != null ) {
            bpmUserGroupDO.memberUserIds( new LinkedHashSet<Long>( set ) );
        }

        return bpmUserGroupDO.build();
    }

    @Override
    public BpmUserGroupRespVO convert(BpmUserGroupDO bean) {
        if ( bean == null ) {
            return null;
        }

        BpmUserGroupRespVO bpmUserGroupRespVO = new BpmUserGroupRespVO();

        bpmUserGroupRespVO.setName( bean.getName() );
        bpmUserGroupRespVO.setDescription( bean.getDescription() );
        Set<Long> set = bean.getMemberUserIds();
        if ( set != null ) {
            bpmUserGroupRespVO.setMemberUserIds( new LinkedHashSet<Long>( set ) );
        }
        bpmUserGroupRespVO.setStatus( bean.getStatus() );
        bpmUserGroupRespVO.setId( bean.getId() );
        bpmUserGroupRespVO.setCreateTime( bean.getCreateTime() );

        return bpmUserGroupRespVO;
    }

    @Override
    public List<BpmUserGroupRespVO> convertList(List<BpmUserGroupDO> list) {
        if ( list == null ) {
            return null;
        }

        List<BpmUserGroupRespVO> list1 = new ArrayList<BpmUserGroupRespVO>( list.size() );
        for ( BpmUserGroupDO bpmUserGroupDO : list ) {
            list1.add( convert( bpmUserGroupDO ) );
        }

        return list1;
    }

    @Override
    public PageResult<BpmUserGroupRespVO> convertPage(PageResult<BpmUserGroupDO> page) {
        if ( page == null ) {
            return null;
        }

        PageResult<BpmUserGroupRespVO> pageResult = new PageResult<BpmUserGroupRespVO>();

        pageResult.setList( convertList( page.getList() ) );
        pageResult.setTotal( page.getTotal() );

        return pageResult;
    }

    @Override
    public List<BpmUserGroupRespVO> convertList2(List<BpmUserGroupDO> list) {
        if ( list == null ) {
            return null;
        }

        List<BpmUserGroupRespVO> list1 = new ArrayList<BpmUserGroupRespVO>( list.size() );
        for ( BpmUserGroupDO bpmUserGroupDO : list ) {
            list1.add( convert( bpmUserGroupDO ) );
        }

        return list1;
    }
}
