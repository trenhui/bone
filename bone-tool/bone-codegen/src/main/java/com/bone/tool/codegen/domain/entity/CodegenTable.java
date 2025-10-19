package com.bone.tool.codegen.domain.entity;

import com.bone.core.domain.entity.AbstractEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Map;

// 移除不存在的@Table注解
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class CodegenTable extends AbstractEntity<Long> implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Long datasourceId;
    private String tableName;
    private String tableComment;
    private String moduleName;
    private String packageName;
    private String businessName;
    private String className;
    private String classComment;
    private String author;
    private Integer templateType;
    private Integer scene;
    private Long parentMenuId;
    private Long masterTableId;
    private Long subJoinColumnId;
    private Boolean subJoinMany;
    private Long treeParentColumnId;
    private Long treeNameColumnId;
    private Map<String, String> codeFiles;
}
