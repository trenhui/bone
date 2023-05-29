package com.bone.module.bpm.convert.definition;

import com.bone.base.core.pojo.PageResult;
import com.bone.module.bpm.controller.admin.definition.vo.form.BpmFormCreateReqVO;
import com.bone.module.bpm.controller.admin.definition.vo.form.BpmFormRespVO;
import com.bone.module.bpm.controller.admin.definition.vo.form.BpmFormSimpleRespVO;
import com.bone.module.bpm.controller.admin.definition.vo.form.BpmFormUpdateReqVO;
import com.bone.module.bpm.dal.dataobject.definition.BpmFormDO;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:37+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class BpmFormConvertImpl implements BpmFormConvert {

    @Override
    public BpmFormDO convert(BpmFormCreateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        BpmFormDO.BpmFormDOBuilder bpmFormDO = BpmFormDO.builder();

        bpmFormDO.name( bean.getName() );
        bpmFormDO.status( bean.getStatus() );
        bpmFormDO.conf( bean.getConf() );
        List<String> list = bean.getFields();
        if ( list != null ) {
            bpmFormDO.fields( new ArrayList<String>( list ) );
        }
        bpmFormDO.remark( bean.getRemark() );

        return bpmFormDO.build();
    }

    @Override
    public BpmFormDO convert(BpmFormUpdateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        BpmFormDO.BpmFormDOBuilder bpmFormDO = BpmFormDO.builder();

        bpmFormDO.id( bean.getId() );
        bpmFormDO.name( bean.getName() );
        bpmFormDO.status( bean.getStatus() );
        bpmFormDO.conf( bean.getConf() );
        List<String> list = bean.getFields();
        if ( list != null ) {
            bpmFormDO.fields( new ArrayList<String>( list ) );
        }
        bpmFormDO.remark( bean.getRemark() );

        return bpmFormDO.build();
    }

    @Override
    public BpmFormRespVO convert(BpmFormDO bean) {
        if ( bean == null ) {
            return null;
        }

        BpmFormRespVO bpmFormRespVO = new BpmFormRespVO();

        bpmFormRespVO.setName( bean.getName() );
        bpmFormRespVO.setStatus( bean.getStatus() );
        bpmFormRespVO.setRemark( bean.getRemark() );
        bpmFormRespVO.setId( bean.getId() );
        bpmFormRespVO.setConf( bean.getConf() );
        List<String> list = bean.getFields();
        if ( list != null ) {
            bpmFormRespVO.setFields( new ArrayList<String>( list ) );
        }
        bpmFormRespVO.setCreateTime( bean.getCreateTime() );

        return bpmFormRespVO;
    }

    @Override
    public List<BpmFormSimpleRespVO> convertList2(List<BpmFormDO> list) {
        if ( list == null ) {
            return null;
        }

        List<BpmFormSimpleRespVO> list1 = new ArrayList<BpmFormSimpleRespVO>( list.size() );
        for ( BpmFormDO bpmFormDO : list ) {
            list1.add( bpmFormDOToBpmFormSimpleRespVO( bpmFormDO ) );
        }

        return list1;
    }

    @Override
    public PageResult<BpmFormRespVO> convertPage(PageResult<BpmFormDO> page) {
        if ( page == null ) {
            return null;
        }

        PageResult<BpmFormRespVO> pageResult = new PageResult<BpmFormRespVO>();

        pageResult.setList( bpmFormDOListToBpmFormRespVOList( page.getList() ) );
        pageResult.setTotal( page.getTotal() );

        return pageResult;
    }

    protected BpmFormSimpleRespVO bpmFormDOToBpmFormSimpleRespVO(BpmFormDO bpmFormDO) {
        if ( bpmFormDO == null ) {
            return null;
        }

        BpmFormSimpleRespVO bpmFormSimpleRespVO = new BpmFormSimpleRespVO();

        bpmFormSimpleRespVO.setId( bpmFormDO.getId() );
        bpmFormSimpleRespVO.setName( bpmFormDO.getName() );

        return bpmFormSimpleRespVO;
    }

    protected List<BpmFormRespVO> bpmFormDOListToBpmFormRespVOList(List<BpmFormDO> list) {
        if ( list == null ) {
            return null;
        }

        List<BpmFormRespVO> list1 = new ArrayList<BpmFormRespVO>( list.size() );
        for ( BpmFormDO bpmFormDO : list ) {
            list1.add( convert( bpmFormDO ) );
        }

        return list1;
    }
}
