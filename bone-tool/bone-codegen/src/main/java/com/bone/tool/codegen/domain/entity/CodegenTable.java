package com.bone.tool.codegen.domain.entity;

import com.bone.core.annotation.Id;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import java.io.Serializable;
import java.util.Map;

/**
 * 代码生成表配置
 * 用于配置代码生成过程中数据表的各项属性和生成规则
 */
@Table("codegen_table")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class CodegenTable extends AbstractEntity<Long> implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @Id
    private Long id;
    
    /** 数据源ID */
    private Long datasourceId;
    
    /** 生成场景 */
    private Integer scene;

    // ========== 数据库表相关属性 ==========

    /** 表名 */
    private String tableName;
    
    /** 表描述 */
    private String tableComment;

    // ========== Java类相关属性 ==========

    /** 模块名 */
    private String moduleName;
    
    /** 包路径 */
    private String packageName;
    
    /** 业务名称 */
    private String businessName;
    
    /** Java类名，首字母大写的驼峰命名 */
    private String className;
    
    /** 类描述 */
    private String classComment;
    
    /** 作者 */
    private String author;

    // ========== 模板配置相关属性 ==========

    /** 模板类型 */
    private Integer templateType;

    // ========== 菜单相关属性 ==========

    /** 父菜单ID */
    private Long parentMenuId;

    // ========== 主子表关系相关属性 ==========

    /** 主表ID */
    private Long masterTableId;
    
    /** 子表关联主表的字段ID */
    private Long subJoinColumnId;
    
    /** 是否一对多关系 */
    private Boolean subJoinMany;

    // ========== 树结构相关属性 ==========

    /** 树表父字段ID */
    private Long treeParentColumnId;
    
    /** 树表名称字段ID */
    private Long treeNameColumnId;
    
    /** 生成的代码文件集合，键为文件路径，值为文件内容 */
    private Map<String, String> codeFiles;

    @Override
    public Long getId() {
        return id;
    }
    
    @Override
    public void setId(Long id) {
        this.id = id;
    }
}
