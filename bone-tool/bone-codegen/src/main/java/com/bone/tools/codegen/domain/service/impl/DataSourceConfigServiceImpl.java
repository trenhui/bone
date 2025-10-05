package com.bone.tools.codegen.domain.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bone.core.exception.BizException;
import com.bone.core.result.PageParam;
import com.bone.core.result.PageResult;
import com.bone.tools.codegen.util.BeanUtils;
import com.bone.tools.codegen.application.dto.DataSourceConfigQueryRequest;
import com.bone.tools.codegen.domain.entity.CodegenTableDO;
import com.bone.tools.codegen.domain.mapper.CodegenTableMapper;
import com.bone.tools.codegen.domain.mapper.DataSourceConfigMapper;
import com.bone.tools.codegen.domain.service.DataSourceConfigService;
import com.bone.tools.codegen.domain.entity.DataSourceConfigDO;
import com.bone.tools.codegen.application.dto.DataSourceConfigSaveRequest;
import com.bone.tools.codegen.infrastructure.util.JdbcUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;


import java.util.List;
import java.util.Objects;

import static com.bone.tools.codegen.domain.enums.ErrorCodeConstants.*;


/**
 * 数据源配置 Service 实现类
 *
 * @author 芋道源码
 */
@Service
@Validated
public class DataSourceConfigServiceImpl implements DataSourceConfigService {

    @Resource
    private DataSourceConfigMapper dataSourceConfigMapper;
    @Resource
    private CodegenTableMapper codegenTableMapper;


    @Override
    public Long createDataSourceConfig(DataSourceConfigSaveRequest createReqVO) {
        LambdaQueryWrapper<DataSourceConfigDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DataSourceConfigDO::getUrl,createReqVO.getUrl());
        queryWrapper.eq(DataSourceConfigDO::getUsername,createReqVO.getUsername());
        List<DataSourceConfigDO> dataSourceConfigDOList = dataSourceConfigMapper.selectList(queryWrapper);
        if(CollectionUtil.isNotEmpty(dataSourceConfigDOList)){
            throw new BizException("已有该数据源，请勿重复创建");
        }
        DataSourceConfigDO config = BeanUtils.toBean(createReqVO, DataSourceConfigDO.class);
        validateConnectionOK(config);
        // 插入
        dataSourceConfigMapper.insert(config);
        // 返回
        return config.getId();
    }

    @Override
    public void updateDataSourceConfig(DataSourceConfigSaveRequest updateReqVO) {
        // 校验存在
        validateDataSourceConfigExists(updateReqVO.getId());
        DataSourceConfigDO updateObj = BeanUtils.toBean(updateReqVO, DataSourceConfigDO.class);
        validateConnectionOK(updateObj);

        // 更新
        dataSourceConfigMapper.updateById(updateObj);
    }

    @Override
    public void deleteDataSourceConfig(Long id) {
        // 校验存在
        validateDataSourceConfigExists(id);
        LambdaQueryWrapper<CodegenTableDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CodegenTableDO::getDataSourceConfigId,id);
        List<CodegenTableDO> codegenTableDOS = codegenTableMapper.selectList(queryWrapper);
        if(CollectionUtil.isNotEmpty(codegenTableDOS)){
            throw new BizException("与该数据源绑定的表还未删除，请先删除");
        }
        // 删除
        dataSourceConfigMapper.deleteById(id);
    }

    private void validateDataSourceConfigExists(Long id) {
        if (dataSourceConfigMapper.selectById(id) == null) {
            throw new RuntimeException(DATA_SOURCE_CONFIG_NOT_EXISTS.getMsg());
        }
    }

    @Override
    public DataSourceConfigDO getDataSourceConfig(Long id) {
        // 如果 id 为 0，默认为 master 的数据源
//        if (Objects.equals(id, DataSourceConfigDO.ID_MASTER)) {
//            return buildMasterDataSourceConfig();
//        }
        // 从 DB 中读取
        return dataSourceConfigMapper.selectById(id);
    }

    @Override
    public PageResult<DataSourceConfigDO> getDataSourceConfigList(PageParam pageParam) {
        IPage<DataSourceConfigDO> page = new Page<>();
        page.setCurrent(pageParam.getPageNo());
        page.setSize(pageParam.getPageSize());
        dataSourceConfigMapper.selectPage(page,new LambdaQueryWrapper<>());
//        List<DataSourceConfigDO> result = dataSourceConfigMapper.selectList(new LambdaQueryWrapper<>());
//        // 补充 master 数据源
//        result.add(0, buildMasterDataSourceConfig());
       return new PageResult<DataSourceConfigDO>(page.getRecords(),pageParam.getPageNo(),pageParam.getPageSize(),(int)page.getTotal());
    }

    @Override
    public List<DataSourceConfigDO> getDataSourceConfigList(DataSourceConfigQueryRequest request) {
        LambdaQueryWrapper<DataSourceConfigDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StringUtils.isNotBlank(request.getName()),DataSourceConfigDO::getName, request.getName())
                .eq(StringUtils.isNotBlank(request.getUrl()),DataSourceConfigDO::getUrl, request.getUrl())
                .eq(Objects.nonNull(request.getId()),DataSourceConfigDO::getId, request.getId())
                .in(CollectionUtil.isNotEmpty(request.getIdList()),DataSourceConfigDO::getId, request.getIdList());
        return dataSourceConfigMapper.selectList(queryWrapper);
    }

    private void validateConnectionOK(DataSourceConfigDO config) {
        boolean success = JdbcUtils.isConnectionOK(config.getUrl(), config.getUsername(), config.getPassword());
        if (!success) {
            throw new RuntimeException(DATA_SOURCE_CONFIG_NOT_OK.getMsg());
        }
    }

    private void validateConfigRepeat(DataSourceConfigDO config,Long id) {
        DataSourceConfigDO dbConfig = dataSourceConfigMapper.selectById(id);
        if (dbConfig != null && StringUtils.equals(dbConfig.getUrl(),config.getUrl()) && StringUtils.equals(dbConfig.getUsername(),config.getUsername())) {
            throw new RuntimeException(DATA_SOURCE_CONFIG_REPEAT.getMsg());
        }
    }

//    private DataSourceConfigDO buildMasterDataSourceConfig() {
//        String primary = dynamicDataSourceProperties.getPrimary();
//        DataSourceProperty dataSourceProperty = dynamicDataSourceProperties.getDatasource().get(primary);
//        return new DataSourceConfigDO().setId(DataSourceConfigDO.ID_MASTER).setName(primary)
//                .setUrl(dataSourceProperty.getUrl())
//                .setUsername(dataSourceProperty.getUsername())
//                .setPassword(dataSourceProperty.getPassword());
//    }

}
