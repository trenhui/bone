package com.bone.lowcode.codegen.domain.service;


import com.bone.core.result.PageParam;
import com.bone.core.result.PageResult;
import com.bone.lowcode.codegen.application.dto.DataSourceConfigQueryRequest;
import com.bone.lowcode.codegen.domain.entity.DataSourceConfigDO;
import com.bone.lowcode.codegen.application.dto.DataSourceConfigSaveRequest;

import java.util.List;


/**
 * 数据源配置 Service 接口
 *
 * @author 芋道源码
 */
public interface DataSourceConfigService {

    /**
     * 创建数据源配置
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createDataSourceConfig( DataSourceConfigSaveRequest createReqVO);

    /**
     * 更新数据源配置
     *
     * @param updateReqVO 更新信息
     */
    void updateDataSourceConfig( DataSourceConfigSaveRequest updateReqVO);

    /**
     * 删除数据源配置
     *
     * @param id 编号
     */
    void deleteDataSourceConfig(Long id);

    /**
     * 获得数据源配置
     *
     * @param id 编号
     * @return 数据源配置
     */
    DataSourceConfigDO getDataSourceConfig(Long id);

    /**
     * 获得数据源配置列表
     *
     * @return 数据源配置列表
     */
    PageResult<DataSourceConfigDO> getDataSourceConfigList(PageParam pageParam);

    List<DataSourceConfigDO> getDataSourceConfigList(DataSourceConfigQueryRequest request);
}
