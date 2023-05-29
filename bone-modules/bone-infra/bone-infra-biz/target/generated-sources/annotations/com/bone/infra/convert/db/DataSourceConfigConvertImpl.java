package com.bone.infra.convert.db;

import com.bone.infra.controller.admin.db.vo.DataSourceConfigCreateReqVO;
import com.bone.infra.controller.admin.db.vo.DataSourceConfigRespVO;
import com.bone.infra.controller.admin.db.vo.DataSourceConfigUpdateReqVO;
import com.bone.infra.dal.dataobject.db.DataSourceConfigDO;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:27:59+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class DataSourceConfigConvertImpl implements DataSourceConfigConvert {

    @Override
    public DataSourceConfigDO convert(DataSourceConfigCreateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        DataSourceConfigDO dataSourceConfigDO = new DataSourceConfigDO();

        dataSourceConfigDO.setName( bean.getName() );
        dataSourceConfigDO.setUrl( bean.getUrl() );
        dataSourceConfigDO.setUsername( bean.getUsername() );
        dataSourceConfigDO.setPassword( bean.getPassword() );

        return dataSourceConfigDO;
    }

    @Override
    public DataSourceConfigDO convert(DataSourceConfigUpdateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        DataSourceConfigDO dataSourceConfigDO = new DataSourceConfigDO();

        dataSourceConfigDO.setId( bean.getId() );
        dataSourceConfigDO.setName( bean.getName() );
        dataSourceConfigDO.setUrl( bean.getUrl() );
        dataSourceConfigDO.setUsername( bean.getUsername() );
        dataSourceConfigDO.setPassword( bean.getPassword() );

        return dataSourceConfigDO;
    }

    @Override
    public DataSourceConfigRespVO convert(DataSourceConfigDO bean) {
        if ( bean == null ) {
            return null;
        }

        DataSourceConfigRespVO dataSourceConfigRespVO = new DataSourceConfigRespVO();

        dataSourceConfigRespVO.setName( bean.getName() );
        dataSourceConfigRespVO.setUrl( bean.getUrl() );
        dataSourceConfigRespVO.setUsername( bean.getUsername() );
        if ( bean.getId() != null ) {
            dataSourceConfigRespVO.setId( bean.getId().intValue() );
        }
        dataSourceConfigRespVO.setCreateTime( bean.getCreateTime() );

        return dataSourceConfigRespVO;
    }

    @Override
    public List<DataSourceConfigRespVO> convertList(List<DataSourceConfigDO> list) {
        if ( list == null ) {
            return null;
        }

        List<DataSourceConfigRespVO> list1 = new ArrayList<DataSourceConfigRespVO>( list.size() );
        for ( DataSourceConfigDO dataSourceConfigDO : list ) {
            list1.add( convert( dataSourceConfigDO ) );
        }

        return list1;
    }
}
