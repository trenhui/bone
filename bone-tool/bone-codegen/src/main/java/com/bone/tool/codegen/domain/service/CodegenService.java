package com.bone.tool.codegen.domain.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.bone.tool.codegen.application.dto.CodegenColumnSaveRequest;
import com.bone.tool.codegen.application.dto.CodegenCreateListRequest;
import com.bone.tool.codegen.application.dto.CodegenTablePageRequest;
import com.bone.tool.codegen.application.dto.CodegenTableSaveRequest;
import com.bone.tool.codegen.application.dto.CodegenUpdateRequest;
import com.bone.tool.codegen.domain.entity.CodegenColumn;
import com.bone.tool.codegen.domain.entity.CodegenTable;
import com.bone.tool.codegen.domain.entity.DataSourceConfig;
import com.bone.tool.codegen.domain.entity.TableField;
import com.bone.tool.codegen.domain.entity.TableInfo;
import com.bone.tool.codegen.domain.repository.CodegenColumnRepository;
import com.bone.tool.codegen.domain.repository.CodegenTableRepository;
import com.bone.tool.codegen.infrastructure.config.CodegenEngine;
import com.bone.metadata.sdk.query.criteria.Criteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 代码生成服务
 * 提供代码生成相关的核心业务逻辑
 *
 * @author bone-team
 */
@Service
public class CodegenService {

    @Autowired
    private CodegenTableRepository codegenTableRepository;

    @Autowired
    private CodegenColumnRepository codegenColumnRepository;

    @Autowired
    private CodegenEngine codegenEngine;

    /**
     * 更新表配置
     */
    public void updateCodegenTable(CodegenUpdateRequest request) {
        if (request == null || request.getTable() == null || request.getTable().getId() == null) {
            throw new RuntimeException("请求参数不能为空");
        }
        
        try {
            Long tableId = request.getTable().getId();
            CodegenTable codegenTable = new CodegenTable();
            codegenTable.setId(tableId);
            codegenTableRepository.update(codegenTable);
            
            if (request.getColumns() != null) {
                for (CodegenColumnSaveRequest columnRequest : request.getColumns()) {
                    CodegenColumn column = new CodegenColumn();
                    column.setTableId(tableId);
                    if (columnRequest.getId() != null) {
                        column.setId(columnRequest.getId());
                        // 使用Repository的update方法代替updateById
                        codegenColumnRepository.update(column);
                    } else {
                        codegenColumnRepository.save(column);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("更新表配置失败");
        }
    }

    /**
     * 删除代码生成列配置
     * 由于Repository接口可能没有deleteById方法，我们使用自定义方法
     */
    private void deleteCodegenColumn(CodegenColumn column) {
        // 实际环境中，这里应该有对应的删除逻辑
        // 这里是简化实现
    }

    /**
     * 同步数据库表结构到代码生成配置
     */
    public void syncCodegenFromDB(Long id) {
        try {
            CodegenTable codegenTable = codegenTableRepository.findById(id);
            if (codegenTable == null) {
                throw new RuntimeException("表配置不存在");
            }
            
            // 假设这里有一个DatabaseTableService可以获取数据库表信息
            TableInfo tableInfo = new TableInfo();
            tableInfo.setName(codegenTable.getTableName());
            tableInfo.setComment(codegenTable.getTableComment());
            
            // 设置实体类名和字段名
            tableInfo.setEntityName(codegenTable.getTableName());
            tableInfo.setFieldName(codegenTable.getTableName());
            
            // 获取表的字段列表
            List<TableField> fields = new ArrayList<>();
            // 这里应该有实际获取数据库字段的逻辑
            // 暂时添加一些模拟数据
            TableField field1 = new TableField();
            field1.setName("id");
            field1.setType("bigint");
            field1.setComment("主键");
            field1.setPrimaryKey(true);
            field1.setPropertyName("id");
            field1.setFill(false);
            fields.add(field1);
            
            TableField field2 = new TableField();
            field2.setName("name");
            field2.setType("varchar");
            field2.setComment("名称");
            field2.setPropertyName("name");
            field2.setFill(false);
            fields.add(field2);
            
            tableInfo.setFields(fields);
            
            // 同步表配置
            syncCodegen0(codegenTable, tableInfo);
        } catch (Exception e) {
            throw new RuntimeException("同步表结构失败：" + e.getMessage());
        }
    }

    /**
     * 执行同步逻辑
     */
    private void syncCodegen0(CodegenTable codegenTable, TableInfo tableInfo) {
        try {
            // 更新表信息
            boolean hasUpdate = false;
            if (!StrUtil.equals(codegenTable.getTableComment(), tableInfo.getComment())) {
                codegenTable.setTableComment(tableInfo.getComment());
                hasUpdate = true;
            }
            
            if (hasUpdate) {
                codegenTableRepository.update(codegenTable);
            }
            
            // 同步字段
            List<CodegenColumn> existingColumns = getColumnsByTableId(codegenTable.getId());
            Map<String, CodegenColumn> columnMap = new HashMap<>();
            for (CodegenColumn column : existingColumns) {
                columnMap.put(column.getColumnName(), column);
            }
            
            List<CodegenColumn> columns = new ArrayList<>();
            if (CollUtil.isNotEmpty(tableInfo.getFields())) {
                for (TableField field : tableInfo.getFields()) {
                    String columnName = field.getName();
                    CodegenColumn column = columnMap.get(columnName);
                    if (column == null) {
                        // 新增字段
                        column = createCodegenColumn(codegenTable.getId(), field);
                        columns.add(column);
                    } else {
                        // 更新字段
                        updateColumn(column, field);
                        columns.add(column);
                        columnMap.remove(columnName);
                    }
                }
            }
            
            // 保存新增和更新的字段
            if (CollUtil.isNotEmpty(columns)) {
                for (CodegenColumn column : columns) {
                    if (column.getId() == null) {
                        codegenColumnRepository.save(column);
                    } else {
                        codegenColumnRepository.update(column);
                    }
                }
            }
            
            // 删除不再存在的字段 - 注意：这里使用的是删除实体对象的方式
            for (CodegenColumn column : columnMap.values()) {
                // 使用自定义方法删除字段（因为Repository接口可能没有deleteById方法）
                deleteCodegenColumn(column);
            }
        } catch (Exception e) {
            throw new RuntimeException("同步失败：" + e.getMessage());
        }
    }

    /**
     * 获取表字段列表
     */
    public List<CodegenColumn> getColumnsByTableId(Long tableId) {
        if (tableId == null) {
            return Collections.emptyList();
        }
        // 使用Criteria构建查询条件
        Criteria<CodegenColumn> criteria = Criteria.<CodegenColumn>builder()
                .eq("tableId", tableId);
        return codegenColumnRepository.findByCriteria(criteria);
    }

    /**
     * 批量生成代码
     */
    public byte[] generateBatchCode(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            throw new RuntimeException("请至少选择一个表");
        }
        
        try {
            // 遍历所有表ID
            for (Long id : ids) {
                CodegenTable codegenTable = codegenTableRepository.findById(id);
                if (codegenTable == null) {
                    throw new RuntimeException("表配置不存在: " + id);
                }
                
                // 获取表的字段列表
                List<CodegenColumn> columns = getColumnsByTableId(id);
                
                // 准备参数
                List<CodegenTable> subTables = new ArrayList<>(); // 暂时没有子表
                List<List<CodegenColumn>> subColumnsList = new ArrayList<>(); // 暂时没有子表字段
                DataSourceConfig dataSourceConfig = new DataSourceConfig(); // 简化处理
                String groupId = "default"; // 简化处理
                Integer modelType = 2; // 默认领域模型类型
                
                // 调用代码生成引擎
                Map<String, String> generatedCode = codegenEngine.execute(
                        codegenTable, columns, subTables, subColumnsList, 
                        dataSourceConfig, groupId, modelType);
                
                // TODO: 处理生成的代码，例如打包成zip文件
                // 这里返回的是简单实现，实际应该返回打包后的字节数组
            }
            
            // 返回一个空的字节数组作为占位符
            return new byte[0];
        } catch (Exception e) {
            throw new RuntimeException("代码生成失败: " + e.getMessage());
        }
    }

    /**
     * 更新列信息
     */
    private void updateColumn(CodegenColumn column, TableField field) {
        // 更新基本信息
        column.setColumnName(field.getName());
        column.setDataType(field.getType());
        column.setDescription(field.getComment());
        
        // 更新Java类型和属性名
        String javaType = getJavaTypeByDbType(field.getType());
        column.setJavaType(javaType);
        column.setJavaField(field.getPropertyName());
        
        // 更新主键信息
        column.setPrimaryKey(field.isPrimaryKey());
        
        // 设置其他属性
        column.setNotNull(true); // 默认非空
        column.setInsertable(true); // 默认可插入
        column.setUpdatable(true); // 默认可更新
        column.setListable(true); // 默认在列表中显示
        column.setQueryable(true); // 默认可查询
        
        // 设置填充信息
        if (field.isFill()) {
            column.setInsertable(true);
            column.setUpdatable(false);
        }
        
        // 调用Repository的update方法
        codegenColumnRepository.update(column);
    }

    /**
     * 创建代码生成列配置
     */
    private CodegenColumn createCodegenColumn(Long tableId, TableField field) {
        CodegenColumn column = new CodegenColumn();
        
        // 设置基本信息
        column.setTableId(tableId);
        column.setColumnName(field.getName());
        column.setDataType(field.getType());
        column.setDescription(field.getComment());
        
        // 设置Java类型和属性名
        String javaType = getJavaTypeByDbType(field.getType());
        column.setJavaType(javaType);
        column.setJavaField(field.getPropertyName());
        
        // 设置主键信息
        column.setPrimaryKey(field.isPrimaryKey());
        
        // 设置其他属性
        column.setNotNull(true); // 默认非空
        column.setInsertable(true); // 默认可插入
        column.setUpdatable(!field.isPrimaryKey()); // 主键不可更新
        column.setListable(true); // 默认在列表中显示
        column.setQueryable(true); // 默认可查询
        
        // 设置默认查询类型
        column.setQueryType("eq");
        
        // 默认显示类型
        column.setShowType("input");
        
        // 设置填充信息
        if (field.isFill()) {
            column.setInsertable(true);
            column.setUpdatable(false);
        }
        
        return column;
    }

    /**
     * 根据数据库类型获取对应的Java类型
     */
    private String getJavaTypeByDbType(String dbType) {
        if (StrUtil.isBlank(dbType)) {
            return "String";
        }
        
        // 转换为小写进行比较
        dbType = dbType.toLowerCase();
        
        if (dbType.contains("char") || dbType.contains("text")) {
            return "String";
        } else if (dbType.contains("int")) {
            return "Integer";
        } else if (dbType.contains("bigint")) {
            return "Long";
        } else if (dbType.contains("float") || dbType.contains("double") || dbType.contains("decimal")) {
            return "BigDecimal";
        } else if (dbType.contains("date") || dbType.contains("time") || dbType.contains("datetime")) {
            return "Date";
        } else if (dbType.contains("boolean")) {
            return "Boolean";
        } else {
            // 默认返回String
            return "String";
        }
    }
}
