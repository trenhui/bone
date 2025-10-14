package com.bone.tool.codegen.domain.service;

import com.bone.core.model.PageParam;
import com.bone.core.model.PageResult;
import com.bone.tool.codegen.application.dto.DataSourceConfigQueryRequest;
import com.bone.tool.codegen.application.dto.DataSourceConfigSaveRequest;
import com.bone.tool.codegen.domain.entity.DataSourceConfig;
import com.bone.tool.codegen.domain.repository.DataSourceConfigRepository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Field;

/**
 * 数据源配置 领域服务实现类
 * <p>
 * 负责数据源配置相关的核心业务逻辑处理
 */
@Service
@Validated
@Slf4j
public class DataSourceConfigService {

    @Autowired
    private DataSourceConfigRepository dataSourceConfigRepository;

    /**
     * 创建数据源配置
     *
     * @param createReqVO 创建信息
     * @return 配置ID
     */
    public Long createDataSourceConfig(DataSourceConfigSaveRequest createReqVO) {
        // 转换为领域实体并保存
        DataSourceConfig config = new DataSourceConfig();
        // 使用反射设置字段值
        setField(config, "name", createReqVO.getName());
        setField(config, "url", createReqVO.getUrl());
        setField(config, "username", createReqVO.getUsername());
        setField(config, "password", createReqVO.getPassword());
        
        // 使用Repository的save方法
        return dataSourceConfigRepository.save(config);
    }

    /**
     * 更新数据源配置
     *
     * @param updateReqVO 更新信息
     */
    public void updateDataSourceConfig(DataSourceConfigSaveRequest updateReqVO) {
        // 获取配置ID并校验存在性
        Long id = updateReqVO.getId();
        validateDataSourceConfigExists(id);
        
        // 转换为领域实体并更新
        DataSourceConfig config = new DataSourceConfig();
        // 使用反射设置字段值
        setField(config, "id", id);
        setField(config, "name", updateReqVO.getName());
        setField(config, "url", updateReqVO.getUrl());
        setField(config, "username", updateReqVO.getUsername());
        setField(config, "password", updateReqVO.getPassword());
        
        // 使用Repository的update方法
        dataSourceConfigRepository.update(config);
    }
    
    /**
     * 使用反射设置对象的私有字段值
     */
    private void setField(Object object, String fieldName, Object value) {
        try {
            Field field = object.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(object, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }

    /**
     * 删除数据源配置
     *
     * @param id 配置ID
     */
    public void deleteDataSourceConfig(Long id) {
        // 校验数据源配置存在
        validateDataSourceConfigExists(id);
        
        // 执行删除操作
        dataSourceConfigRepository.deleteById(id);
    }

    /**
     * 获取数据源配置
     *
     * @param id 配置ID
     * @return 数据源配置
     */
    public DataSourceConfig getDataSourceConfig(Long id) {
        // 获取数据源配置详情
        DataSourceConfig config = dataSourceConfigRepository.findById(id);
        if (config == null) {
            throw new RuntimeException("数据源配置不存在");
        }
        return config;
    }

    /**
     * 分页获取数据源配置列表
     *
     * @param pageParam 分页参数
     * @return 数据源配置分页结果
     */
    public PageResult<DataSourceConfig> getDataSourceConfigPage(PageParam pageParam) {
        return getDataSourceConfigPage(null, pageParam);
    }

    /**
     * 根据查询条件获取数据源配置列表
     *
     * @param request 查询条件
     * @return 数据源配置列表
     */
    public List<DataSourceConfig> getDataSourceConfigList(DataSourceConfigQueryRequest request) {
        // 简化实现，直接使用空Criteria返回所有数据
        Criteria<DataSourceConfig> criteria = Criteria.<DataSourceConfig>builder();
        return dataSourceConfigRepository.findByCriteria(criteria);
    }

    /**
     * 获取所有数据源配置列表
     * 
     * @return 数据源配置列表
     */
    public List<DataSourceConfig> getDataSourceConfigList() {
        // 使用Criteria获取所有数据源配置
        Criteria<DataSourceConfig> criteria = Criteria.<DataSourceConfig>builder();
        return dataSourceConfigRepository.findByCriteria(criteria);
    }
    
    /**
     * 分页查询数据源配置
     * 
     * @param queryReqVO 查询条件
     * @param pageParam 分页参数
     * @return 分页结果
     */
    public PageResult<DataSourceConfig> getDataSourceConfigPage(DataSourceConfigQueryRequest queryReqVO, PageParam pageParam) {
        // 构建查询条件
        Criteria<DataSourceConfig> criteria = Criteria.<DataSourceConfig>builder();
        
        // 设置分页参数
        if (pageParam != null) {
            criteria.page(pageParam.getPage(), pageParam.getSize());
        }
        
        // 使用Repository的pageByCriteria方法
        return dataSourceConfigRepository.pageByCriteria(criteria);
    }

    /**
     * 校验数据源配置是否存在
     * 
     * @param id 数据源配置ID
     * @throws RuntimeException 当数据源配置不存在时抛出异常
     */
    private void validateDataSourceConfigExists(Long id) {
        if (dataSourceConfigRepository.findById(id) == null) {
            throw new RuntimeException("数据源配置不存在");
        }
    }

    /**
     * 校验数据源连接是否正常
     * 
     * @param config 数据源配置
     * @throws RuntimeException 当连接异常时抛出异常
     */
    private void validateConnectionOK(DataSourceConfig config) {
        // 实际实现应该验证数据库连接是否正常
        // 可以使用JdbcUtils进行连接测试
    }
}
