package com.bone.module.system.convert.sensitiveword;

import com.bone.base.core.pojo.PageResult;
import com.bone.module.system.controller.admin.sensitiveword.vo.SensitiveWordCreateReqVO;
import com.bone.module.system.controller.admin.sensitiveword.vo.SensitiveWordExcelVO;
import com.bone.module.system.controller.admin.sensitiveword.vo.SensitiveWordRespVO;
import com.bone.module.system.controller.admin.sensitiveword.vo.SensitiveWordUpdateReqVO;
import com.bone.module.system.dal.dataobject.sensitiveword.SensitiveWordDO;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:15+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class SensitiveWordConvertImpl implements SensitiveWordConvert {

    @Override
    public SensitiveWordDO convert(SensitiveWordCreateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        SensitiveWordDO.SensitiveWordDOBuilder sensitiveWordDO = SensitiveWordDO.builder();

        sensitiveWordDO.name( bean.getName() );
        sensitiveWordDO.description( bean.getDescription() );
        List<String> list = bean.getTags();
        if ( list != null ) {
            sensitiveWordDO.tags( new ArrayList<String>( list ) );
        }
        sensitiveWordDO.status( bean.getStatus() );

        return sensitiveWordDO.build();
    }

    @Override
    public SensitiveWordDO convert(SensitiveWordUpdateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        SensitiveWordDO.SensitiveWordDOBuilder sensitiveWordDO = SensitiveWordDO.builder();

        sensitiveWordDO.id( bean.getId() );
        sensitiveWordDO.name( bean.getName() );
        sensitiveWordDO.description( bean.getDescription() );
        List<String> list = bean.getTags();
        if ( list != null ) {
            sensitiveWordDO.tags( new ArrayList<String>( list ) );
        }
        sensitiveWordDO.status( bean.getStatus() );

        return sensitiveWordDO.build();
    }

    @Override
    public SensitiveWordRespVO convert(SensitiveWordDO bean) {
        if ( bean == null ) {
            return null;
        }

        SensitiveWordRespVO sensitiveWordRespVO = new SensitiveWordRespVO();

        sensitiveWordRespVO.setName( bean.getName() );
        List<String> list = bean.getTags();
        if ( list != null ) {
            sensitiveWordRespVO.setTags( new ArrayList<String>( list ) );
        }
        sensitiveWordRespVO.setStatus( bean.getStatus() );
        sensitiveWordRespVO.setDescription( bean.getDescription() );
        sensitiveWordRespVO.setId( bean.getId() );
        sensitiveWordRespVO.setCreateTime( bean.getCreateTime() );

        return sensitiveWordRespVO;
    }

    @Override
    public List<SensitiveWordRespVO> convertList(List<SensitiveWordDO> list) {
        if ( list == null ) {
            return null;
        }

        List<SensitiveWordRespVO> list1 = new ArrayList<SensitiveWordRespVO>( list.size() );
        for ( SensitiveWordDO sensitiveWordDO : list ) {
            list1.add( convert( sensitiveWordDO ) );
        }

        return list1;
    }

    @Override
    public PageResult<SensitiveWordRespVO> convertPage(PageResult<SensitiveWordDO> page) {
        if ( page == null ) {
            return null;
        }

        PageResult<SensitiveWordRespVO> pageResult = new PageResult<SensitiveWordRespVO>();

        pageResult.setList( convertList( page.getList() ) );
        pageResult.setTotal( page.getTotal() );

        return pageResult;
    }

    @Override
    public List<SensitiveWordExcelVO> convertList02(List<SensitiveWordDO> list) {
        if ( list == null ) {
            return null;
        }

        List<SensitiveWordExcelVO> list1 = new ArrayList<SensitiveWordExcelVO>( list.size() );
        for ( SensitiveWordDO sensitiveWordDO : list ) {
            list1.add( sensitiveWordDOToSensitiveWordExcelVO( sensitiveWordDO ) );
        }

        return list1;
    }

    protected SensitiveWordExcelVO sensitiveWordDOToSensitiveWordExcelVO(SensitiveWordDO sensitiveWordDO) {
        if ( sensitiveWordDO == null ) {
            return null;
        }

        SensitiveWordExcelVO sensitiveWordExcelVO = new SensitiveWordExcelVO();

        sensitiveWordExcelVO.setId( sensitiveWordDO.getId() );
        sensitiveWordExcelVO.setName( sensitiveWordDO.getName() );
        List<String> list = sensitiveWordDO.getTags();
        if ( list != null ) {
            sensitiveWordExcelVO.setTags( new ArrayList<String>( list ) );
        }
        sensitiveWordExcelVO.setStatus( sensitiveWordDO.getStatus() );
        sensitiveWordExcelVO.setDescription( sensitiveWordDO.getDescription() );
        sensitiveWordExcelVO.setCreateTime( sensitiveWordDO.getCreateTime() );

        return sensitiveWordExcelVO;
    }
}
