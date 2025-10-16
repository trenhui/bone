package com.bone.smartmeta.engine.metadata;

import com.bone.smartmeta.engine.annotation.FieldType;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 增强的字段元数据模型类
 * 支持AI功能、计算字段、虚拟字段和动态显示控制
 */
@Getter
@Setter
public class FieldMetadata {

    // 字段API名称
    private String apiName;
    
    // 显示标签
    private String label;
    
    // 字段类型
    private FieldType type;
    
    // 描述
    private String description;
    
    // 是否必填
    private boolean required;
    
    // 是否唯一
    private boolean unique;
    
    // 长度
    private int length = 255;
    
    // 数字精度
    private int precision;
    
    // 小数位数
    private int scale;
    
    // 默认值
    private String defaultValue;
    
    // 格式模式
    private String pattern;
    
    // 引用的实体
    private String referenceTo;
    
    // 选择列表值
    private List<String> picklistValues = new ArrayList<>();
    
    // 是否建立索引
    private boolean indexed;
    
    // 是否为主键
    private boolean primaryKey;
    
    // 是否为系统字段
    private boolean systemField;
    
    // Java字段名称
    private String fieldName;
    
    // 数据库列名
    private String columnName;
    
    // 是否加密
    private boolean encrypted;
    
    // 加密算法
    private String encryptionAlgorithm;
    
    // 是否可搜索
    private boolean searchable = true;
    
    // 是否可排序
    private boolean sortable = true;
    
    // 公式表达式
    private String formulaExpression;
    
    // 公式返回类型
    private FieldType formulaReturnType;
    
    // 重算配置
    private RecalculationMetadata recalculation;
    
    // 业务规则
    private List<BusinessRuleMetadata> businessRules = new ArrayList<>();
    
    // AI相关配置
    // 是否启用AI自动填充
    private Boolean aiAutoFillEnabled;
    
    // AI提示模板
    private String aiPrompt;
    
    // 数据敏感度级别
    private String sensitivityLevel;
    
    // 计算字段相关配置
    // 计算表达式
    private String calculationExpression;
    
    // 计算依赖字段列表
    private List<String> calculationDependencies;
    
    // 是否为虚拟字段
    private boolean virtual;
    
    // 字段显示配置
    // 字段分组
    private String fieldGroup;
    
    // 是否在列表中显示
    private boolean showInList = true;
    
    // 是否在详情中显示
    private boolean showInDetail = true;
    
    // 最小值（用于数值类型）
    private Double minValue;
    
    // 最大值（用于数值类型）
    private Double maxValue;
    
    // 最小长度（用于字符串类型）
    private Integer minLength;
    
    // 最大长度（用于字符串类型）
    private Integer maxLength;

    /**
     * 添加业务规则
     */
    public void addBusinessRule(BusinessRuleMetadata rule) {
        businessRules.add(rule);
    }
    
    /**
     * 获取字段API名称
     */
    public String getApiName() {
        return apiName;
    }
    
    /**
     * 设置字段API名称
     */
    public void setApiName(String apiName) {
        this.apiName = apiName;
    }
    
    /**
     * 获取显示标签
     */
    public String getLabel() {
        return label;
    }
    
    /**
     * 设置显示标签
     */
    public void setLabel(String label) {
        this.label = label;
    }
    
    /**
     * 检查是否必填
     */
    public boolean isRequired() {
        return required;
    }
    
    /**
     * 设置是否必填
     */
    public void setRequired(boolean required) {
        this.required = required;
    }
    
    /**
     * 检查是否为虚拟字段
     */
    public boolean isVirtual() {
        return virtual;
    }
    
    /**
     * 获取计算表达式
     */
    public String getCalculationExpression() {
        return calculationExpression;
    }
    
    /**
     * 获取正则表达式模式
     */
    public String getRegexPattern() {
        return pattern;
    }
    
    /**
     * 获取最小值
     */
    public Double getMinValue() {
        return minValue;
    }
    
    /**
     * 设置最小值
     */
    public void setMinValue(Double minValue) {
        this.minValue = minValue;
    }
    
    /**
     * 获取最大值
     */
    public Double getMaxValue() {
        return maxValue;
    }
    
    /**
     * 设置最大值
     */
    public void setMaxValue(Double maxValue) {
        this.maxValue = maxValue;
    }
    
    /**
     * 获取最小长度
     */
    public Integer getMinLength() {
        return minLength;
    }
    
    /**
     * 设置最小长度
     */
    public void setMinLength(Integer minLength) {
        this.minLength = minLength;
    }
    
    /**
     * 获取最大长度
     */
    public Integer getMaxLength() {
        return maxLength;
    }
    
    /**
     * 设置最大长度
     */
    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }
    
    /**
     * 设置是否为虚拟字段
     */
    public void setVirtual(boolean virtual) {
        this.virtual = virtual;
    }
    
    /**
     * 设置计算表达式
     */
    public void setCalculationExpression(String calculationExpression) {
        this.calculationExpression = calculationExpression;
    }
    
    /**
     * 设置正则表达式模式
     */
    // 修改参数类型为String，避免类型不兼容问题
    public void setRegexPattern(String pattern) {
        this.pattern = pattern;
    }
    
    /**
     * 设置物理名称
     */
    public void setPhysicalName(String physicalName) {
        this.columnName = physicalName;
    }
    
    /**
     * 设置字段类型
     */
    public void setType(String type) {
        // 根据字符串类型创建FieldType枚举实例
        try {
            this.type = FieldType.valueOf(type.toUpperCase());
        } catch (Exception e) {
            // 如果类型无效，使用默认类型，避免使用不存在的STRING常量
            try {
                this.type = FieldType.valueOf("STRING");
            } catch (Exception ex) {
                // 如果STRING也不存在，暂时将type设置为null
                this.type = null;
            }
        }
    }
    
    /**
     * 设置描述
     */
    public void setDescription(String description) {
        this.description = description;
    }
    
    /**
     * 设置默认值
     */
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }
}
