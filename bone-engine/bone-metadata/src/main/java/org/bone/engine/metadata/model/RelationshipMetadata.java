package org.bone.engine.metadata.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 关系元数据模型 - 定义实体之间的关联关系
 * 支持一对一、一对多、多对一、多对多等关系类型
 * 
 * @author Bone Engine Team
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RelationshipMetadata {

    // ================ 核心属性 ================
    
    /**
     * 关系名称
     */
    private String name;
    
    /**
     * 关系标签
     */
    private String label;
    
    /**
     * 关系类型
     */
    private String type;
    
    /**
     * 目标实体
     */
    private String targetEntity;
    
    /**
     * 源字段
     */
    private String sourceField;
    
    /**
     * 目标字段
     */
    private String targetField;
    
    /**
     * 级联操作类型
     */
    private String cascade;
    
    /**
     * 是否移除孤立记录
     */
    private Boolean orphanRemoval;
    
    /**
     * 批量加载大小
     */
    private Integer batchSize;
    
    /**
     * 加载策略
     */
    private String fetchType;
    
    /**
     * 是否必填关系
     */
    private Boolean required;
    
    /**
     * 关系描述
     */
    private String description;
    
    /**
     * UI元数据
     */
    private RelationshipUIMetadata uiMetadata;
    
    // ================ 构造方法与辅助方法 ================
    
    public RelationshipMetadata() {
        this.cascade = "NONE";
        this.orphanRemoval = Boolean.FALSE;
        this.fetchType = "LAZY";
        this.required = Boolean.FALSE;
        this.uiMetadata = new RelationshipUIMetadata();
    }
    
    /**
     * 检查是否为一对多关系
     */
    public boolean isOneToMany() {
        return "ONE_TO_MANY".equals(this.type);
    }
    
    /**
     * 检查是否为多对一关系
     */
    public boolean isManyToOne() {
        return "MANY_TO_ONE".equals(this.type);
    }
    
    /**
     * 检查是否为一对一关系
     */
    public boolean isOneToOne() {
        return "ONE_TO_ONE".equals(this.type);
    }
    
    /**
     * 检查是否为多对多关系
     */
    public boolean isManyToMany() {
        return "MANY_TO_MANY".equals(this.type);
    }
    
    /**
     * 检查是否为集合类型关系（一对多或多对多）
     */
    public boolean isCollectionRelationship() {
        return isOneToMany() || isManyToMany();
    }
    
    /**
     * 检查是否启用级联保存
     */
    public boolean hasCascadePersist() {
        return "ALL".equals(this.cascade) || "PERSIST".equals(this.cascade);
    }
    
    /**
     * 检查是否启用级联删除
     */
    public boolean hasCascadeRemove() {
        return "ALL".equals(this.cascade) || "REMOVE".equals(this.cascade);
    }
    
    /**
     * 检查是否为懒加载
     */
    public boolean isLazyFetch() {
        return "LAZY".equals(this.fetchType);
    }
    
    /**
     * 检查是否为急加载
     */
    public boolean isEagerFetch() {
        return "EAGER".equals(this.fetchType);
    }
    
    /**
     * 关系UI元数据内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RelationshipUIMetadata {
        
        /**
         * 网格列数
         */
        private Integer gridColumns = 12;
        
        /**
         * 是否允许添加
         */
        private Boolean allowAdd = Boolean.TRUE;
        
        /**
         * 是否允许删除
         */
        private Boolean allowDelete = Boolean.TRUE;
        
        /**
         * 是否允许编辑
         */
        private Boolean allowEdit = Boolean.TRUE;
        
        /**
         * 显示模式
         */
        private String displayMode = "TABLE";
    }
}