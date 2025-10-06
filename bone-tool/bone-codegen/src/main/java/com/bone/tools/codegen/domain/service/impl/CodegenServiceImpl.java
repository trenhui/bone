package com.bone.tools.codegen.domain.service.impl;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.generator.config.po.TableField;
import com.baomidou.mybatisplus.generator.config.po.TableInfo;
import com.bone.core.model.PageResult;
import com.bone.tools.codegen.util.BeanUtils;
import com.bone.tools.codegen.application.dto.CodegenTablePageRequest;
import com.bone.tools.codegen.domain.entity.DataSourceConfigDO;
import com.bone.tools.codegen.domain.mapper.DataSourceConfigMapper;
import com.bone.tools.codegen.domain.mapper.CodegenColumnMapper;
import com.bone.tools.codegen.domain.mapper.CodegenTableMapper;
import com.bone.tools.codegen.domain.service.CodegenService;
import com.bone.tools.codegen.domain.service.DatabaseTableService;
import com.bone.tools.codegen.infrastructure.config.CodegenBuilder;
import com.bone.tools.codegen.infrastructure.config.CodegenEngine;
import com.bone.tools.codegen.domain.enums.CodegenSceneEnum;
import com.bone.tools.codegen.domain.enums.CodegenTemplateTypeEnum;
import com.bone.tools.codegen.domain.entity.CodegenColumnDO;
import com.bone.tools.codegen.domain.entity.CodegenTableDO;
import com.bone.tools.codegen.application.dto.CodegenCreateListRequest;
import com.bone.tools.codegen.application.dto.CodegenUpdateRequest;
import com.bone.tools.codegen.application.dto.DatabaseTableResponse;
import com.bone.tools.codegen.infrastructure.util.CollectionUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import static com.bone.tools.codegen.infrastructure.util.CollectionUtils.convertMap;
import static com.bone.tools.codegen.infrastructure.util.CollectionUtils.convertSet;
import static com.bone.tools.codegen.infrastructure.util.ServiceExceptionUtil.exception;
import static com.bone.tools.codegen.domain.enums.ErrorCodeConstants.*;


/**
 * 代码生成 Service 实现类
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class CodegenServiceImpl implements CodegenService {

    @Resource
    private DatabaseTableService databaseTableService;

    @Resource
    private CodegenTableMapper codegenTableMapper;
    @Resource
    private CodegenColumnMapper codegenColumnMapper;

    @Resource
    private CodegenBuilder codegenBuilder;
    @Resource
    private CodegenEngine codegenEngine;

//    @Resource
//    private CodegenProperties codegenProperties;
    @Resource
    private DataSourceConfigMapper dataSourceConfigMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> createCodegenList(Long userId, CodegenCreateListRequest reqVO) {
        List<Long> ids = new ArrayList<>(reqVO.getTableNames().size());
        try {
            reqVO.getTableNames().forEach(tableName ->
                    ids.add(createCodegen(userId, reqVO.getDataSourceConfigId(), tableName)));
        } catch (Exception ex) {
            log.error("[createCodegenList] Error occurred", ex);
            throw ex; // 重新抛出以确保事务回滚和异常传播
        }
        return ids;
    }

    private Long createCodegen(Long userId, Long dataSourceConfigId, String tableName) {
        // 从数据库中，获得数据库表结构
        TableInfo tableInfo = databaseTableService.getTable(dataSourceConfigId, tableName);
        // 导入
        return createCodegen0(userId, dataSourceConfigId, tableInfo);
    }

    private Long createCodegen0(Long userId, Long dataSourceConfigId, TableInfo tableInfo) {
        // 校验导入的表和字段非空
        validateTableInfo(tableInfo);
        // 校验是否已经存在
        if (codegenTableMapper.selectByTableNameAndDataSourceConfigId(tableInfo.getName(),
                dataSourceConfigId) != null) {
            throw exception(CODEGEN_TABLE_EXISTS);
        }

        // 构建 CodegenTableDO 对象，插入到 DB 中
        CodegenTableDO table = codegenBuilder.buildTable(tableInfo);
        table.setDataSourceConfigId(dataSourceConfigId);
        table.setScene(CodegenSceneEnum.ADMIN.getScene()); // 默认配置下，使用管理后台的模板
        //table.setFrontType(codegenProperties.getFrontType());
        //table.setAuthor(userApi.getUser(userId).getCheckedData().getNickname());
        table.setAuthor(userId.toString());
        codegenTableMapper.insert(table);
        long tableId = table.getId();
        //int tableId = codegenTableMapper.
        // 构建 CodegenColumnDO 数组，插入到 DB 中
        List<CodegenColumnDO> columns = codegenBuilder.buildColumns((long)tableId, tableInfo.getFields());
        // 如果没有主键，则使用第一个字段作为主键
        if (!tableInfo.isHavePrimaryKey()) {
            columns.get(0).setPrimaryKey(true);
        }
        columns.forEach(x-> codegenColumnMapper.insert(x));
        return table.getId();
    }

    //@VisibleForTesting
    void validateTableInfo(TableInfo tableInfo) {
        if (tableInfo == null) {
            throw exception(CODEGEN_IMPORT_TABLE_NULL);
        }
        if (StrUtil.isEmpty(tableInfo.getComment())) {
            throw exception(CODEGEN_TABLE_INFO_TABLE_COMMENT_IS_NULL);
        }
        if (CollUtil.isEmpty(tableInfo.getFields())) {
            throw exception(CODEGEN_IMPORT_COLUMNS_NULL);
        }
        tableInfo.getFields().forEach(field -> {
            if (StrUtil.isEmpty(field.getComment())) {
                throw exception(CODEGEN_TABLE_INFO_COLUMN_COMMENT_IS_NULL, field.getName());
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCodegen(CodegenUpdateRequest updateReqVO) {
        // 校验是否已经存在
        if (codegenTableMapper.selectById(updateReqVO.getTable().getId()) == null) {
            throw exception(CODEGEN_TABLE_NOT_EXISTS);
        }
        // 校验主表字段存在
        if (Objects.equals(updateReqVO.getTable().getTemplateType(), CodegenTemplateTypeEnum.SUB.getType())) {
            if (codegenTableMapper.selectById(updateReqVO.getTable().getMasterTableId()) == null) {
                throw exception(CODEGEN_MASTER_TABLE_NOT_EXISTS, updateReqVO.getTable().getMasterTableId());
            }
            if (CollUtil.findOne(updateReqVO.getColumns(),  // 关联主表的字段不存在
                    column -> column.getId().equals(updateReqVO.getTable().getSubJoinColumnId())) == null) {
                throw exception(CODEGEN_SUB_COLUMN_NOT_EXISTS, updateReqVO.getTable().getSubJoinColumnId());
            }
        }

        // 更新 table 表定义
        CodegenTableDO updateTableObj = BeanUtils.toBean(updateReqVO.getTable(), CodegenTableDO.class);
        codegenTableMapper.updateById(updateTableObj);
        // 更新 column 字段定义
        List<CodegenColumnDO> updateColumnObjs = BeanUtils.toBean(updateReqVO.getColumns(), CodegenColumnDO.class);
        updateColumnObjs.forEach(updateColumnObj -> codegenColumnMapper.updateById(updateColumnObj));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncCodegenFromDB(Long tableId) {
        // 校验是否已经存在
        CodegenTableDO table = codegenTableMapper.selectById(tableId);
        if (table == null) {
            throw exception(CODEGEN_TABLE_NOT_EXISTS);
        }
        // 从数据库中，获得数据库表结构
        TableInfo tableInfo = databaseTableService.getTable(table.getDataSourceConfigId(), table.getTableName());
        // 执行同步
        syncCodegen0(tableId, tableInfo);
    }

    private void syncCodegen0(Long tableId, TableInfo tableInfo) {
        // 1. 校验导入的表和字段非空
        validateTableInfo(tableInfo);
        List<TableField> tableFields = tableInfo.getFields();

        // 2. 构建 CodegenColumnDO 数组，只同步新增的字段
        List<CodegenColumnDO> codegenColumns = codegenColumnMapper.selectListByTableId(tableId);
        Set<String> codegenColumnNames = CollectionUtils.convertSet(codegenColumns, CodegenColumnDO::getColumnName);

        // 3.1 计算需要【修改】的字段，插入时重新插入，删除时将原来的删除
        Map<String, CodegenColumnDO> codegenColumnDOMap = CollectionUtils.convertMap(codegenColumns, CodegenColumnDO::getColumnName);
        BiPredicate<TableField, CodegenColumnDO> primaryKeyPredicate =
                (tableField, codegenColumn) -> tableField.getMetaInfo().getJdbcType().name().equals(codegenColumn.getDataType())
                        && tableField.getMetaInfo().isNullable() == codegenColumn.getNullable()
                        && tableField.isKeyFlag() == codegenColumn.getPrimaryKey()
                        && tableField.getComment().equals(codegenColumn.getColumnComment());
        Set<String> modifyFieldNames = tableFields.stream()
                .filter(tableField -> codegenColumnDOMap.get(tableField.getColumnName()) != null
                        && !primaryKeyPredicate.test(tableField, codegenColumnDOMap.get(tableField.getColumnName())))
                .map(TableField::getColumnName)
                .collect(Collectors.toSet());
        // 3.2 计算需要【删除】的字段
        Set<String> tableFieldNames = CollectionUtils.convertSet(tableFields, TableField::getName);
        Set<Long> deleteColumnIds = codegenColumns.stream()
                .filter(column -> (!tableFieldNames.contains(column.getColumnName())) || modifyFieldNames.contains(column.getColumnName()))
                .map(CodegenColumnDO::getId).collect(Collectors.toSet());
        // 移除已经存在的字段
        tableFields.removeIf(column -> codegenColumnNames.contains(column.getColumnName()) && (!modifyFieldNames.contains(column.getColumnName())));
        if (CollUtil.isEmpty(tableFields) && CollUtil.isEmpty(deleteColumnIds)) {
            throw exception(CODEGEN_SYNC_NONE_CHANGE);
        }

        // 4.1 插入新增的字段
        List<CodegenColumnDO> columns = codegenBuilder.buildColumns(tableId, tableFields);
        columns.forEach(x->codegenColumnMapper.insert(x));
        // 4.2 删除不存在的字段
        if (CollUtil.isNotEmpty(deleteColumnIds)) {
            codegenColumnMapper.deleteBatchIds(deleteColumnIds);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCodegen(Long tableId) {
        // 校验是否已经存在
        if (codegenTableMapper.selectById(tableId) == null) {
            throw exception(CODEGEN_TABLE_NOT_EXISTS);
        }

        // 删除 table 表定义
        codegenTableMapper.deleteById(tableId);
        // 删除 column 字段定义
        codegenColumnMapper.deleteListByTableId(tableId);
    }

    @Override
    public List<CodegenTableDO> getCodegenTableList(Long dataSourceConfigId) {
        return codegenTableMapper.selectListByDataSourceConfigId(dataSourceConfigId);
    }

    @Override
    public PageResult<CodegenTableDO> getCodegenTablePage(CodegenTablePageRequest pageReqVO) {
        return codegenTableMapper.selectPage(pageReqVO);
    }

    @Override
    public CodegenTableDO getCodegenTable(Long id) {
        return codegenTableMapper.selectById(id);
    }

    @Override
    public List<CodegenColumnDO> getCodegenColumnListByTableId(Long tableId) {
        return codegenColumnMapper.selectListByTableId(tableId);
    }

    @Override
    public Map<String, String> generationCodes(Long tableId,Integer modelType) {
        // 校验是否已经存在
        CodegenTableDO table = codegenTableMapper.selectById(tableId);
        if (table == null) {
            throw exception(CODEGEN_TABLE_NOT_EXISTS);
        }
        List<CodegenColumnDO> columns = codegenColumnMapper.selectListByTableId(tableId);
        if (CollUtil.isEmpty(columns)) {
            throw exception(CODEGEN_COLUMN_NOT_EXISTS);
        }
        DataSourceConfigDO dataSourceConfigDO = dataSourceConfigMapper.selectById(table.getDataSourceConfigId());

        // 如果是主子表，则加载对应的子表信息
        List<CodegenTableDO> subTables = null;
        List<List<CodegenColumnDO>> subColumnsList = null;
        if (CodegenTemplateTypeEnum.isMaster(table.getTemplateType())) {
            // 校验子表存在
            subTables = codegenTableMapper.selectListByTemplateTypeAndMasterTableId(
                    CodegenTemplateTypeEnum.SUB.getType(), tableId);
            if (CollUtil.isEmpty(subTables)) {
                throw exception(CODEGEN_MASTER_GENERATION_FAIL_NO_SUB_TABLE);
            }
            // 校验子表的关联字段存在
            subColumnsList = new ArrayList<>();
            for (CodegenTableDO subTable : subTables) {
                List<CodegenColumnDO> subColumns = codegenColumnMapper.selectListByTableId(subTable.getId());
                if (CollUtil.findOne(subColumns, column -> column.getId().equals(subTable.getSubJoinColumnId())) == null) {
                    throw exception(CODEGEN_SUB_COLUMN_NOT_EXISTS, subTable.getId());
                }
                subColumnsList.add(subColumns);
            }
        }

        // 执行生成
        return codegenEngine.execute(table, columns, subTables, subColumnsList,dataSourceConfigDO,null,modelType);
    }

    @Override
    public Map<String, String> generationCodes(List<Long> tableIdList,String basePackeage,String model,String groupId,Integer modelType) {
        Map<String,String> result = new HashMap<>();
        for(long tableId:tableIdList) {
            // 校验是否已经存在
            CodegenTableDO table = codegenTableMapper.selectById(tableId);
            table.setModuleName(model);
            table.setPackgeName(basePackeage);
            if (table == null) {
                throw exception(CODEGEN_TABLE_NOT_EXISTS);
            }
            List<CodegenColumnDO> columns = codegenColumnMapper.selectListByTableId(tableId);
            if (CollUtil.isEmpty(columns)) {
                throw exception(CODEGEN_COLUMN_NOT_EXISTS);
            }
            DataSourceConfigDO dataSourceConfigDO = dataSourceConfigMapper.selectById(table.getDataSourceConfigId());

            // 如果是主子表，则加载对应的子表信息
            List<CodegenTableDO> subTables = null;
            List<List<CodegenColumnDO>> subColumnsList = null;
            if (CodegenTemplateTypeEnum.isMaster(table.getTemplateType())) {
                // 校验子表存在
                subTables = codegenTableMapper.selectListByTemplateTypeAndMasterTableId(
                        CodegenTemplateTypeEnum.SUB.getType(), tableId);
                if (CollUtil.isEmpty(subTables)) {
                    throw exception(CODEGEN_MASTER_GENERATION_FAIL_NO_SUB_TABLE);
                }
                // 校验子表的关联字段存在
                subColumnsList = new ArrayList<>();
                for (CodegenTableDO subTable : subTables) {
                    List<CodegenColumnDO> subColumns = codegenColumnMapper.selectListByTableId(subTable.getId());
                    if (CollUtil.findOne(subColumns, column -> column.getId().equals(subTable.getSubJoinColumnId())) == null) {
                        throw exception(CODEGEN_SUB_COLUMN_NOT_EXISTS, subTable.getId());
                    }
                    subColumnsList.add(subColumns);
                }
            }
            result.putAll(codegenEngine.execute(table, columns, subTables, subColumnsList,dataSourceConfigDO,groupId,modelType));
        }
        // 执行生成
        return result;
    }

    @Override
    public List<DatabaseTableResponse> getDatabaseTableList(Long dataSourceConfigId, String name, String comment) {
        List<TableInfo> tables = databaseTableService.getTableList(dataSourceConfigId, name, comment);
        // 移除在 Codegen 中，已经存在的
        Set<String> existsTables = CollectionUtils.convertSet(
                codegenTableMapper.selectListByDataSourceConfigId(dataSourceConfigId), CodegenTableDO::getTableName);
        tables.removeIf(table -> existsTables.contains(table.getName()));
        return BeanUtils.toBean(tables, DatabaseTableResponse.class);
    }

}
