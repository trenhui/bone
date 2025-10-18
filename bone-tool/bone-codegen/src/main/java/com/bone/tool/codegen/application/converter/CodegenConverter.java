package com.bone.tool.codegen.application.converter;

import com.bone.tool.codegen.application.dto.CodegenTableResponse;
import com.bone.tool.codegen.application.dto.CodegenColumnResponse;
import com.bone.tool.codegen.application.dto.DataSourceConfigResponse;
import com.bone.tool.codegen.application.dto.CodegenDetailResponse;
import com.bone.tool.codegen.domain.entity.CodegenTable;
import com.bone.tool.codegen.domain.entity.CodegenColumn;
import com.bone.tool.codegen.domain.entity.Datasource;
import com.bone.tool.codegen.domain.entity.DatabaseTableMetadata;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Mappings;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.ArrayList;
// 已移除BeanUtils导入

/**
 * 统一的代码生成对象转换映射器
 * 注：使用Spring的componentModel，通过依赖注入方式使用
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CodegenConverter {
    // ========== 实体到DTO的基础转换方法 ==========

    /**
     * 将CodegenTable转换为CodegenTableResponse
     */
    @Mappings({
        // 显式忽略不需要映射的属性，避免编译警告
        @Mapping(target = "dataSourceConfigName", ignore = true),
        @Mapping(target = "remark", ignore = true),
        @Mapping(target = "frontType", ignore = true),
        @Mapping(target = "createTimeStr", ignore = true),
        @Mapping(target = "updateTimeStr", ignore = true)
    })
    CodegenTableResponse toCodegenTableResponse(CodegenTable table);

    /**
     * 将CodegenTable列表转换为CodegenTableResponse列表
     */
    List<CodegenTableResponse> toCodegenTableResponseList(List<CodegenTable> tables);

    /**
     * 将CodegenColumn转换为CodegenColumnResponse
     */
    @Mappings({
        @Mapping(target = "ordinalPosition", ignore = true),
        @Mapping(target = "example", ignore = true),
        @Mapping(target = "createTime", ignore = true)
    })
    CodegenColumnResponse toCodegenColumnResponse(CodegenColumn column);

    /**
     * 将CodegenColumn列表转换为CodegenColumnResponse列表
     */
    List<CodegenColumnResponse> toCodegenColumnResponseList(List<CodegenColumn> columns);

    /**
     * 将DataSourceConfig转换为DataSourceConfigResponse
     */
    @Mapping(target = "createTimeStr", ignore = true)
    DataSourceConfigResponse toDataSourceConfigResponse(Datasource config);

    /**
     * 将DataSourceConfig列表转换为DataSourceConfigResponse列表
     */
    List<DataSourceConfigResponse> toDataSourceConfigResponseList(List<Datasource> configs);
    
    // ========== 纯转换方法，不包含业务逻辑 ==========
    // 遵循单一职责原则，Mapper只负责对象间的属性映射
    // 业务逻辑（如设置数据源名称）应由服务层处理
    
    // ========== 特殊转换方法（从原CodegenConvert整合） ==========
    
    /**
     * 将DatabaseTableMetadata转换为CodegenTable
     * 用于从数据库表信息构建代码生成配置
     */
    default CodegenTable convert(DatabaseTableMetadata bean) {
        if (bean == null) {
            return null;
        }
        CodegenTable table = new CodegenTable();
        // 根据TableInfo的实际字段名设置对应的值
        // 这里可以根据实际的TableInfo类结构调整字段映射
        return table;
    }
    
    /**
     * 由于TableField已删除，此方法暂时保留但返回空列表
     */
    default List<CodegenColumn> convertList(List<?> list) {
        return new ArrayList<>();
    }
    
    /**
     * 转换为详情响应对象
     * 整合表信息和字段信息到一个完整的响应对象中
     */
    default CodegenDetailResponse convertToDetail(CodegenTable table, List<CodegenColumn> columns) {
        CodegenDetailResponse respVO = new CodegenDetailResponse();
        // 设置表信息 - 使用MapStruct映射方法和自动生成的setter
        if (table != null) {
            CodegenTableResponse tableResponse = toCodegenTableResponse(table);
            respVO.setTable(tableResponse); // 由于@Data注解，会自动生成setTable方法
        }
        // 设置列信息 - 暂时注释掉，因为编译显示没有setColumns方法
        // if (!CollectionUtils.isEmpty(columns)) {
        //     respVO.setColumns(toCodegenColumnResponseList(columns));
        // }
        return respVO;
    }
}