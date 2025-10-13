package com.bone.tools.codegen.domain.service;


import com.bone.core.model.PageParam;
import com.bone.core.model.PageResult;
import com.bone.tools.codegen.application.dto.DataSourceConfigQueryRequest;
import com.bone.tools.codegen.domain.entity.DataSourceConfigDO;
import com.bone.tools.codegen.application.dto.DataSourceConfigSaveRequest;

import java.util.List;


/**
 * 数据源配置 领域服务接口
 * <p>
 * 负责数据源配置相关的核心业务逻辑处理
 */
public interface DataSourceConfigService {

    /**
     * 创建数据源配置
     *
     * @param createReqVO 创建信息
     * @return 配置ID
     */
    Long createDataSourceConfig(DataSourceConfigSaveRequest createReqVO);

    /**
     * 更新数据源配置
     *
     * @param updateReqVO 更新信息
     */
    void updateDataSourceConfig(DataSourceConfigSaveRequest updateReqVO);

    /**
     * 删除数据源配置
     *
     * @param id 配置ID
     */
    void deleteDataSourceConfig(Long id);

    /**
     * 获取数据源配置
     *
     * @param id 配置ID
     * @return 数据源配置
     */
    DataSourceConfigDO getDataSourceConfig(Long id);

    /**
     * 分页获取数据源配置列表
     *
     * @param pageParam 分页参数
     * @return 数据源配置分页结果
     */
    PageResult<DataSourceConfigDO> getDataSourceConfigPage(PageParam pageParam);

    /**
     * 根据查询条件获取数据源配置列表
     *
     * @param request 查询条件
     * @return 数据源配置列表
     */
    List<DataSourceConfigDO> getDataSourceConfigList(DataSourceConfigQueryRequest request);
}
