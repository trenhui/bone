package com.bone.tool.codegen.domain.entity;

import java.io.Serializable;
import lombok.Data;
import com.bone.core.domain.entity.Entity;

/**
 * 代码生成列配置
 */
@Data
public class CodegenColumn extends Entity<Long> implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long tableId;
    private String columnName;
    private String dataType;
    private String columnComment;
    private String javaType;
    private String javaField;
    private Boolean primaryKey;
    private Boolean autoIncrement;
    private Boolean nullable;
    private Boolean createOperation;
    private Boolean updateOperation;
    private Boolean listOperation;
    private Boolean listOperationResult;
    private String listOperationCondition;
    private String htmlType;
    private String dictType;
    private String relationTableName;
    private String relationShowField;
    private String relationQueryField;
    private String extraAttrs;
}
