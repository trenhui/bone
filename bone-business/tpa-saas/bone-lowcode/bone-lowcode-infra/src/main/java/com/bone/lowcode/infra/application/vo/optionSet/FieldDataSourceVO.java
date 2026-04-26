package com.bone.lowcode.infra.application.vo.optionSet;


import com.bone.lowcode.infra.infrastructure.persistence.dto.FieldExtraConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class FieldDataSourceVO {

    /**
     * 业务字段编码
     */
    private String fieldCode;

    /**
     * 业务字段名称
     */
    private String fieldName;

    /**
     * 字段组件类型
     */
    private String componentType;

    /**
     * 基础字段数据源类型,1:选项集,2:主数据
     */
    private Byte baseSourceType;

    /**
     * 基础字段数据源code
     */
    private String baseSourceCode;

    /**
     * 基础字段数据源名称
     */
    private String baseSourceName;

    /**
     * 专属页面字段数据源类型,1:选项集,2:主数据
     */
    private Byte exclusiveSourceType;

    /**
     * 专属页面字段数据源code
     */
    private String exclusiveSourceCode;

    /**
     * 专属页面字段数据源名称
     */
    private String exclusiveSourceName;


    private FieldExtraConfig  extraConfig  = new FieldExtraConfig();
}
