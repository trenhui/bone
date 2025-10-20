package org.bone.engine.metadata.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 索引元数据模型 - 定义实体的数据库索引配置
 * 支持复合索引、唯一索引、全文索引等
 * 
 * @author Bone Engine Team
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndexMetadata {

    // ================ 核心属性 ================
    
    /**
     * 索引名称
     */
    private String name;
    
    /**
     * 索引类型
     */
    private String type;
    
    /**
     * 索引字段列表
     */
    private List<IndexField> fields;
    
    /**
     * 是否唯一索引
     */
    private Boolean unique;
    
    /**
     * 是否空间索引
     */
    private Boolean spatial;
    
    /**
     * 索引注释
     */
    private String comment;
    
    /**
     * 索引配置参数
     */
    private Map<String, Object> config;
    
    /**
     * 是否启用
     */
    private Boolean enabled;
    
    // ================ 构造方法与辅助方法 ================
    
    public IndexMetadata() {
        this.type = "BTREE";
        this.fields = new ArrayList<>();
        this.unique = Boolean.FALSE;
        this.spatial = Boolean.FALSE;
        this.enabled = Boolean.TRUE;
    }
    
    /**
     * 添加索引字段
     */
    public IndexMetadata addField(IndexField field) {
        if (this.fields == null) {
            this.fields = new ArrayList<>();
        }
        this.fields.add(field);
        return this;
    }
    
    /**
     * 添加简单索引字段（默认升序）
     */
    public IndexMetadata addField(String fieldName) {
        if (this.fields == null) {
            this.fields = new ArrayList<>();
        }
        this.fields.add(new IndexField(fieldName));
        return this;
    }
    
    /**
     * 添加索引字段（指定排序方向）
     */
    public IndexMetadata addField(String fieldName, String direction) {
        if (this.fields == null) {
            this.fields = new ArrayList<>();
        }
        this.fields.add(new IndexField(fieldName, direction));
        return this;
    }
    
    /**
     * 检查是否为复合索引
     */
    public boolean isCompositeIndex() {
        return this.fields != null && this.fields.size() > 1;
    }
    
    /**
     * 检查是否为BTREE索引
     */
    public boolean isBTreeIndex() {
        return "BTREE".equals(this.type);
    }
    
    /**
     * 检查是否为HASH索引
     */
    public boolean isHashIndex() {
        return "HASH".equals(this.type);
    }
    
    /**
     * 检查是否为全文索引
     */
    public boolean isFulltextIndex() {
        return "FULLTEXT".equals(this.type);
    }
    
    /**
     * 检查是否为GIST索引
     */
    public boolean isGistIndex() {
        return "GIST".equals(this.type);
    }
    
    /**
     * 检查是否为GIN索引
     */
    public boolean isGinIndex() {
        return "GIN".equals(this.type);
    }
    
    /**
     * 获取索引的所有字段名列表
     */
    public List<String> getFieldNames() {
        List<String> fieldNames = new ArrayList<>();
        if (this.fields != null) {
            for (IndexField field : this.fields) {
                fieldNames.add(field.getName());
            }
        }
        return fieldNames;
    }
    
    /**
     * 索引字段内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class IndexField {
        
        /**
         * 字段名
         */
        private String name;
        
        /**
         * 排序方向
         */
        private String direction;
        
        /**
         * 字段长度限制
         */
        private Integer length;
        
        /**
         * 字段排序规则
         */
        private String collation;
        
        /**
         * 字段选项
         */
        private String options;
        
        public IndexField() {
            this.direction = "ASC";
        }
        
        public IndexField(String name) {
            this.name = name;
            this.direction = "ASC";
        }
        
        public IndexField(String name, String direction) {
            this.name = name;
            this.direction = direction;
        }
        
        /**
         * 检查是否为升序
         */
        public boolean isAscending() {
            return "ASC".equals(this.direction);
        }
        
        /**
         * 检查是否为降序
         */
        public boolean isDescending() {
            return "DESC".equals(this.direction);
        }
    }
    
    /**
     * 创建唯一索引的静态工厂方法
     */
    public static IndexMetadata createUniqueIndex(String name, String... fieldNames) {
        IndexMetadata index = new IndexMetadata();
        index.setName(name);
        index.setUnique(Boolean.TRUE);
        
        for (String fieldName : fieldNames) {
            index.addField(fieldName);
        }
        
        return index;
    }
    
    /**
     * 创建复合索引的静态工厂方法
     */
    public static IndexMetadata createCompositeIndex(String name, String... fieldNames) {
        IndexMetadata index = new IndexMetadata();
        index.setName(name);
        
        for (String fieldName : fieldNames) {
            index.addField(fieldName);
        }
        
        return index;
    }
    
    /**
     * 创建全文索引的静态工厂方法
     */
    public static IndexMetadata createFulltextIndex(String name, String... fieldNames) {
        IndexMetadata index = new IndexMetadata();
        index.setName(name);
        index.setType("FULLTEXT");
        
        for (String fieldName : fieldNames) {
            index.addField(fieldName);
        }
        
        return index;
    }
}