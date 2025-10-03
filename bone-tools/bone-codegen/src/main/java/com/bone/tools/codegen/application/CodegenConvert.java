package com.bone.tools.codegen.application;

import com.baomidou.mybatisplus.generator.config.po.TableField;
import com.baomidou.mybatisplus.generator.config.po.TableInfo;
import com.bone.core.util.BeanUtils;
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
import java.util.Map;

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

    default CodegenDetailResponse convert(CodegenTableDO table, List<CodegenColumnDO> columns) {
        CodegenDetailResponse respVO = new CodegenDetailResponse();
        respVO.setTable(BeanUtils.toBean(table, CodegenTableResponse.class));
        respVO.setColumns(BeanUtils.toBean(columns, CodegenColumnResponse.class));
        return respVO;
    }

    default List<CodegenPreviewResponse> convert(Map<String, String> codes) {
        return CollectionUtils.convertList(codes.entrySet(),
                entry -> {
                    CodegenPreviewResponse vo = new CodegenPreviewResponse();
            vo.setFilePath(entry.getKey());
            vo.setCode(entry.getValue());
            return vo;
        });
    }

}
