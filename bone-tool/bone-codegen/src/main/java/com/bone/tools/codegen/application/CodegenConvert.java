package com.bone.tools.codegen.application;

import java.util.Map;

import com.baomidou.mybatisplus.generator.config.po.TableField;
import com.baomidou.mybatisplus.generator.config.po.TableInfo;
import com.bone.tools.codegen.util.BeanUtils;
import com.bone.tools.codegen.application.dto.CodegenTableResponse;
import com.bone.tools.codegen.domain.entity.CodegenColumnDO;
import com.bone.tools.codegen.domain.entity.CodegenTableDO;
import com.bone.tools.codegen.application.dto.CodegenDetailResponse;
import com.bone.tools.codegen.application.dto.CodegenPreviewResponse;
import com.bone.tools.codegen.application.dto.CodegenColumnResponse;
import com.bone.tools.codegen.infrastructure.util.CollectionUtils;
import org.apache.ibatis.type.JdbcType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface CodegenConvert {

    CodegenConvert INSTANCE = Mappers.getMapper(CodegenConvert.class);

    // ========== TableInfo 相关 ==========

    @Mappings({
            @Mapping(source = "name", target = "tableName"),
            @Mapping(source = "comment", target = "tableComment"),
    })
    CodegenTableDO convert(TableInfo bean);

    List<CodegenColumnDO> convertList(List<TableField> list);

    @Mappings({
            @Mapping(source = "name", target = "columnName"),
            @Mapping(source = "metaInfo.jdbcType", target = "dataType", qualifiedByName = "getDataType"),
            @Mapping(source = "comment", target = "columnComment"),
            @Mapping(source = "metaInfo.nullable", target = "nullable"),
            @Mapping(source = "keyFlag", target = "primaryKey"),
            @Mapping(source = "columnType.type", target = "javaType"),
            @Mapping(source = "propertyName", target = "javaField"),
    })
    CodegenColumnDO convert(TableField bean);

    @Named("getDataType")
    default String getDataType(JdbcType jdbcType) {
        return jdbcType.name();
    }

    // ========== 其它 ==========

    default CodegenDetailResponse convertToDetail(CodegenTableDO table, List<CodegenColumnDO> columns) {
        // 使用简单的构造方式，避免使用setter方法
        CodegenDetailResponse respVO = new CodegenDetailResponse();
        // 直接访问字段（如果有必要可以考虑使用反射）
        try {
            // 设置table字段
            java.lang.reflect.Field tableField = CodegenDetailResponse.class.getDeclaredField("table");
            tableField.setAccessible(true);
            tableField.set(respVO, BeanUtils.toBean(table, CodegenTableResponse.class));
            
            // 设置columns字段
            java.lang.reflect.Field columnsField = CodegenDetailResponse.class.getDeclaredField("columns");
            columnsField.setAccessible(true);
            columnsField.set(respVO, BeanUtils.toBean(columns, CodegenColumnResponse.class));
        } catch (Exception e) {
            // 如果反射失败，返回空对象
        }
        return respVO;
    }

    default List<CodegenPreviewResponse> convertToPreview(Map<String, String> codes) {
        return CollectionUtils.convertList(codes.entrySet(),
                entry -> {
                    CodegenPreviewResponse vo = new CodegenPreviewResponse();
            // 直接访问字段
            try {
                java.lang.reflect.Field filePathField = CodegenPreviewResponse.class.getDeclaredField("filePath");
                filePathField.setAccessible(true);
                filePathField.set(vo, entry.getKey());
                
                java.lang.reflect.Field codeField = CodegenPreviewResponse.class.getDeclaredField("code");
                codeField.setAccessible(true);
                codeField.set(vo, entry.getValue());
            } catch (Exception e) {
                // 如果反射失败，忽略错误
            }
            return vo;
        });
    }

}
