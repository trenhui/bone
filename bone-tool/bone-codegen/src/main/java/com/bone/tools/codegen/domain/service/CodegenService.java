package com.bone.tools.codegen.domain.service;

import com.bone.core.model.PageResult;
import com.bone.tools.codegen.domain.entity.CodegenColumnDO;
import com.bone.tools.codegen.domain.entity.CodegenTableDO;
import com.bone.tools.codegen.application.dto.CodegenCreateListRequest;
import com.bone.tools.codegen.application.dto.CodegenUpdateRequest;
import com.bone.tools.codegen.application.dto.CodegenTablePageRequest;
import com.bone.tools.codegen.application.dto.DatabaseTableResponse;

import java.util.List;
import java.util.Map;

/**
 * 代码生成 领域服务接口
 * <p>
 * 作为代码生成领域的核心服务，负责协调领域实体、规则和业务流程，
 * 提供代码生成相关的核心业务能力，是领域层与应用层交互的主要入口。
 *
 * @author bone-team
 */
public interface CodegenService {

    /**
     * 批量创建代码生成表配置
     * <p>
     * 基于数据库表结构，创建代码生成所需的表配置实体
     *
     * @param userId 用户ID
     * @param reqVO 创建请求参数
     * @return 创建的表配置ID列表
     */
    List<Long> createCodegenTableList(Long userId, CodegenCreateListRequest reqVO);

    /**
     * 更新代码生成表配置
     * <p>
     * 更新表配置及其关联的字段配置信息
     *
     * @param updateReqVO 更新请求参数
     */
    void updateCodegenTable(CodegenUpdateRequest updateReqVO);

    /**
     * 从数据库同步代码生成表配置
     * <p>
     * 根据数据库最新表结构，同步更新代码生成表配置信息
     *
     * @param tableId 表配置ID
     */
    void syncCodegenFromDB(Long tableId);

    /**
     * 删除代码生成表配置
     * <p>
     * 删除指定的表配置及其关联的字段配置信息
     *
     * @param tableId 表配置ID
     */
    void deleteCodegenTable(Long tableId);

    /**
     * 获取指定数据源的代码生成表配置列表
     *
     * @param dataSourceConfigId 数据源配置ID
     * @return 表配置列表
     */
    List<CodegenTableDO> getCodegenTableList(Long dataSourceConfigId);

    /**
     * 分页获取代码生成表配置列表
     *
     * @param pageReqVO 分页查询参数
     * @return 表配置分页结果
     */
    PageResult<CodegenTableDO> getCodegenTablePage(CodegenTablePageRequest pageReqVO);

    /**
     * 获取代码生成表配置详情
     *
     * @param id 表配置ID
     * @return 表配置实体
     */
    CodegenTableDO getCodegenTable(Long id);

    /**
     * 获取指定表配置的字段配置列表
     *
     * @param tableId 表配置ID
     * @return 字段配置列表
     */
    List<CodegenColumnDO> getCodegenColumnListByTableId(Long tableId);

    /**
     * 生成指定表配置的代码
     *
     * @param tableId 表配置ID
     * @param modelType 模型类型
     * @return 生成结果映射。key为文件路径，value为对应的代码内容
     */
    Map<String, String> generateCode(Long tableId, Integer modelType);

    /**
     * 批量生成多个表配置的代码
     *
     * @param tableIdList 表配置ID列表
     * @param basePackage 基础包名
     * @param model 模块名称
     * @param groupId 组ID
     * @param modelType 模型类型
     * @return 生成结果映射。key为文件路径，value为对应的代码内容
     */
    Map<String, String> generateBatchCode(List<Long> tableIdList, String basePackage, String model, String groupId, Integer modelType);

    /**
     * 获取数据库中的表信息列表
     * <p>
     * 从指定数据源获取可用的数据库表列表，排除已导入的表
     *
     * @param dataSourceConfigId 数据源配置ID
     * @param name 表名称（支持模糊查询）
     * @param comment 表描述（支持模糊查询）
     * @return 数据库表信息列表
     */
    List<DatabaseTableResponse> getDatabaseTableList(Long dataSourceConfigId, String name, String comment);

}
