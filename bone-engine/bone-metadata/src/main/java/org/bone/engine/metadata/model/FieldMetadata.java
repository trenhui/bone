package org.bone.engine.metadata.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 字段元数据模型 - 定义实体中的单个字段属性
 * 包含字段的类型、约束、UI配置、权限等完整信息
 * 
 * @author Bone Engine Team
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldMetadata {

    // ================ 核心属性 ================
    
    /**
     * 字段名称
     */
    private String name;
    
    /**
     * 显示标签
     */
    private String label;
    
    /**
     * 多语言标签
     */
    private Map<String, String> labels;
    
    /**
     * 字段描述
     */
    private String description;
    
    /**
     * 字段类型
     */
    private String type;
    
    /**
     * 是否必填
     */
    private Boolean required;
    
    /**
     * 默认值
     */
    private Object defaultValue;
    
    /**
     * 默认值表达式
     */
    private String defaultExpression;
    
    /**
     * 最大长度
     */
    private Integer maxLength;
    
    /**
     * 最小值
     */
    private Double minValue;
    
    /**
     * 最大值
     */
    private Double maxValue;
    
    /**
     * 是否计算字段
     */
    private Boolean calculated;
    
    /**
     * 计算表达式
     */
    private String calculationExpression;
    
    /**
     * 是否加密存储
     */
    private Boolean encrypted;
    
    /**
     * 选择列表值
     */
    private List<PicklistValue> picklistValues;
    
    /**
     * 字段权限设置
     */
    private FieldPermission permission;
    
    /**
     * UI元数据
     */
    private FieldUIMetadata uiMetadata;
    
    /**
     * 是否为非结构化数据
     */
    private Boolean unstructured;
    
    // ================ 扩展属性 ================
    
    /**
     * 是否建立索引
     */
    private Boolean indexed;
    
    /**
     * 是否可搜索
     */
    private Boolean searchable;
    
    /**
     * 是否可排序
     */
    private Boolean sortable;
    
    /**
     * 是否跟踪历史变更
     */
    private Boolean trackHistory;
    
    /**
     * 是否启用AI自动填充
     */
    private Boolean aiAutoFillEnabled;
    
    /**
     * 数值精度（总位数）
     */
    private Integer precision;
    
    /**
     * 小数位数
     */
    private Integer scale;
    
    /**
     * 是否唯一
     */
    private Boolean unique;
    
    /**
     * 是否只读
     */
    private Boolean readOnly;
    
    /**
     * 验证正则表达式
     */
    private String validationPattern;
    
    /**
     * 输入占位符
     */
    private String placeholder;
    
    /**
     * 帮助文本
     */
    private String helpText;
    
    /**
     * 显示格式
     */
    private String displayFormat;
    
    // ================ 构造方法与辅助方法 ================
    
    public FieldMetadata() {
        this.required = Boolean.FALSE;
        this.calculated = Boolean.FALSE;
        this.encrypted = Boolean.FALSE;
        this.unstructured = Boolean.FALSE;
        this.indexed = Boolean.FALSE;
        this.searchable = Boolean.FALSE;
        this.sortable = Boolean.FALSE;
        this.trackHistory = Boolean.FALSE;
        this.aiAutoFillEnabled = Boolean.FALSE;
        this.unique = Boolean.FALSE;
        this.readOnly = Boolean.FALSE;
        this.labels = new HashMap<>();
        this.permission = new FieldPermission();
        this.uiMetadata = new FieldUIMetadata();
    }
    
    /**
     * 添加选择列表值
     */
    public FieldMetadata addPicklistValue(PicklistValue value) {
        if (this.picklistValues == null) {
            this.picklistValues = new java.util.ArrayList<>();
        }
        this.picklistValues.add(value);
        return this;
    }
    
    /**
     * 获取选择列表值映射（值 -> 标签）
     */
    public Map<String, String> getPicklistValueMap() {
        Map<String, String> valueMap = new HashMap<>();
        if (this.picklistValues != null) {
            for (PicklistValue value : this.picklistValues) {
                valueMap.put(value.getValue(), value.getLabel());
            }
        }
        return valueMap;
    }
    
    /**
     * 检查是否为选择列表类型
     */
    public boolean isPicklistType() {
        return "PICKLIST".equals(this.type);
    }
    
    /**
     * 检查是否为查找类型
     */
    public boolean isLookupType() {
        return "LOOKUP".equals(this.type);
    }
    
    /**
     * 检查是否为数值类型
     */
    public boolean isNumericType() {
        return "NUMBER".equals(this.type) || "INTEGER".equals(this.type) || "CURRENCY".equals(this.type);
    }
    
    /**
     * 检查是否为日期类型
     */
    public boolean isDateType() {
        return "DATE".equals(this.type) || "DATETIME".equals(this.type) || "TIMESTAMP".equals(this.type);
    }
    
    /**
     * 检查是否为复杂类型（JSON、OBJECT、ARRAY）
     */
    public boolean isComplexType() {
        return "JSON".equals(this.type) || "OBJECT".equals(this.type) || "ARRAY".equals(this.type);
    }
    
    /**
     * 获取选择列表值的标签
     */
    public String getPicklistLabelByValue(String value) {
        if (this.picklistValues != null) {
            for (PicklistValue pv : this.picklistValues) {
                if (pv.getValue().equals(value)) {
                    return pv.getLabel();
                }
            }
        }
        return value;
    }
    
    /**
     * 字段权限内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldPermission {
        private Boolean readable = Boolean.TRUE;
        private Boolean writable = Boolean.TRUE;
        private Boolean visible = Boolean.TRUE;
        private List<String> permissionGroups;
        private String conditionalExpression;
    }
    
    /**
     * 字段UI元数据内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldUIMetadata {
        private String component;
        private Integer width;
        private Integer height;
        private Integer orderIndex = 0;
        private Integer gridColumns = 12;
        private String format;
        private String align = "left";
        private Map<String, Object> style;
        private Boolean hidden = Boolean.FALSE;
        private Boolean readonly = Boolean.FALSE;
        private String conditionalVisibilityExpression;
        private String lookupDisplayField;
        private List<String> lookupSearchFields;
    }
    
    /**
     * 选择列表值内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PicklistValue {
        private String value;
        private String label;
        private String description;
        private Boolean disabled = Boolean.FALSE;
        private Integer sortOrder = 0;
    }
}