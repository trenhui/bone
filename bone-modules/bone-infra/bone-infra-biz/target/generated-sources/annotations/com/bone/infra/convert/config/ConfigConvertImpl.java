package com.bone.infra.convert.config;

import com.bone.base.core.pojo.PageResult;
import com.bone.infra.controller.admin.config.vo.ConfigCreateReqVO;
import com.bone.infra.controller.admin.config.vo.ConfigExcelVO;
import com.bone.infra.controller.admin.config.vo.ConfigRespVO;
import com.bone.infra.controller.admin.config.vo.ConfigUpdateReqVO;
import com.bone.infra.dal.dataobject.config.ConfigDO;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:27:59+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class ConfigConvertImpl implements ConfigConvert {

    @Override
    public PageResult<ConfigRespVO> convertPage(PageResult<ConfigDO> page) {
        if ( page == null ) {
            return null;
        }

        PageResult<ConfigRespVO> pageResult = new PageResult<ConfigRespVO>();

        pageResult.setList( configDOListToConfigRespVOList( page.getList() ) );
        pageResult.setTotal( page.getTotal() );

        return pageResult;
    }

    @Override
    public ConfigRespVO convert(ConfigDO bean) {
        if ( bean == null ) {
            return null;
        }

        ConfigRespVO configRespVO = new ConfigRespVO();

        configRespVO.setKey( bean.getConfigKey() );
        configRespVO.setCategory( bean.getCategory() );
        configRespVO.setName( bean.getName() );
        configRespVO.setValue( bean.getValue() );
        configRespVO.setVisible( bean.getVisible() );
        configRespVO.setRemark( bean.getRemark() );
        configRespVO.setId( bean.getId() );
        configRespVO.setType( bean.getType() );
        configRespVO.setCreateTime( bean.getCreateTime() );

        return configRespVO;
    }

    @Override
    public ConfigDO convert(ConfigCreateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        ConfigDO configDO = new ConfigDO();

        configDO.setConfigKey( bean.getKey() );
        configDO.setCategory( bean.getCategory() );
        configDO.setName( bean.getName() );
        configDO.setValue( bean.getValue() );
        configDO.setVisible( bean.getVisible() );
        configDO.setRemark( bean.getRemark() );

        return configDO;
    }

    @Override
    public ConfigDO convert(ConfigUpdateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        ConfigDO configDO = new ConfigDO();

        configDO.setId( bean.getId() );
        configDO.setCategory( bean.getCategory() );
        configDO.setName( bean.getName() );
        configDO.setValue( bean.getValue() );
        configDO.setVisible( bean.getVisible() );
        configDO.setRemark( bean.getRemark() );

        return configDO;
    }

    @Override
    public List<ConfigExcelVO> convertList(List<ConfigDO> list) {
        if ( list == null ) {
            return null;
        }

        List<ConfigExcelVO> list1 = new ArrayList<ConfigExcelVO>( list.size() );
        for ( ConfigDO configDO : list ) {
            list1.add( configDOToConfigExcelVO( configDO ) );
        }

        return list1;
    }

    protected List<ConfigRespVO> configDOListToConfigRespVOList(List<ConfigDO> list) {
        if ( list == null ) {
            return null;
        }

        List<ConfigRespVO> list1 = new ArrayList<ConfigRespVO>( list.size() );
        for ( ConfigDO configDO : list ) {
            list1.add( convert( configDO ) );
        }

        return list1;
    }

    protected ConfigExcelVO configDOToConfigExcelVO(ConfigDO configDO) {
        if ( configDO == null ) {
            return null;
        }

        ConfigExcelVO configExcelVO = new ConfigExcelVO();

        configExcelVO.setId( configDO.getId() );
        configExcelVO.setName( configDO.getName() );
        configExcelVO.setValue( configDO.getValue() );
        configExcelVO.setType( configDO.getType() );
        configExcelVO.setRemark( configDO.getRemark() );
        configExcelVO.setCreateTime( configDO.getCreateTime() );

        return configExcelVO;
    }
}
