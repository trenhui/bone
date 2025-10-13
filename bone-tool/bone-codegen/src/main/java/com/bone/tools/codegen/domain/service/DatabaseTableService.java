package com.bone.tools.codegen.domain.service;

import com.baomidou.mybatisplus.generator.config.po.TableInfo;

import java.util.List;

/**
 * 数据库表 领域服务接口
 * <p>
 * 负责数据库表结构信息的获取和处理，为代码生成提供底层数据源支持
 *
 * @author bone-team
 */
public interface DatabaseTableService {

    /**
     * 获取数据库表列表
     * <p>
     * 基于表名称和表描述进行模糊匹配，从指定数据源获取表信息
     *
     * @param dataSourceConfigId 数据源配置ID
     * @param nameLike 表名称（模糊匹配）
     * @param commentLike 表描述（模糊匹配）
     * @return 表信息列表
     */
    List<TableInfo> getTableList(Long dataSourceConfigId, String nameLike, String commentLike);

    /**
     * 获取指定数据库表信息
     *
     * @param dataSourceConfigId 数据源配置ID
     * @param tableName 表名称
     * @return 表信息
     */
    TableInfo getTable(Long dataSourceConfigId, String tableName);

}
