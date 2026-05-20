package com.bone.metadata.engine.model;

import java.util.List;
import java.util.Map;
import lombok.Data;

/** 字段元数据模型 注意：此类与org.bone.engine.metadata.model.FieldMetadata存在功能重叠 当前版本保持独立实现，后续可考虑统一元数据模型 */
@Data
public class FieldMetadata {

  /** 字段ID */
  private String id;

  /** 字段名称 */
  private String name;

  /** 字段API名称 */
  private String apiName;

  /** 字段描述 */
  private String description;

  /** 数据库列名 */
  private String columnName;

  /** 数据类型 */
  private DataType dataType;

  /** 是否必填 */
  private boolean required;

  /** 是否主键 */
  private boolean primaryKey;

  /** 是否自增 */
  private boolean autoIncrement;

  /** 是否显示名称字段 */
  private boolean displayName;

  /** 默认值 */
  private String defaultValue;

  /** 字段长度 */
  private Integer length;

  /** 小数点精度 */
  private Integer precision;

  /** 小数位数 */
  private Integer scale;

  /** 枚举选项 */
  private List<EnumOption> enumOptions;

  /** 是否可计算 */
  private boolean calculated;

  /** 计算表达式 */
  private String calculationExpression;

  /** 验证表达式 */
  private String validationExpression;

  /** 验证错误消息 */
  private String errorMessage;

  /** 获取字段名称（兼容方法，返回name属性） */
  public String getName() {
    return this.name;
  }

  /** 是否可搜索 */
  private boolean searchable;

  /** 是否可排序 */
  private boolean sortable;

  /** 是否可筛选 */
  private boolean filterable;

  /** 是否可编辑 */
  private boolean editable;

  /** 是否可导出 */
  private boolean exportable;

  /** UI配置 */
  private UIConfig uiConfig;

  /** 扩展属性 */
  private Map<String, Object> extendedProperties;

  /** 数据类型枚举 */
  public enum DataType {
    STRING, // 字符串
    INTEGER, // 整数
    LONG, // 长整数
    DOUBLE, // 浮点数
    DECIMAL, // 高精度小数
    BOOLEAN, // 布尔值
    DATE, // 日期
    DATETIME, // 日期时间
    TIME, // 时间
    TEXT, // 长文本
    JSON, // JSON格式
    BLOB, // 二进制大对象
    ENUM, // 枚举类型
    REFERENCE // 引用类型
  }

  /** 枚举选项 */
  @Data
  public static class EnumOption {
    private String label;
    private Object value;
    private String description;
  }

  /** UI配置 */
  @Data
  public static class UIConfig {
    private String componentType;
    private Map<String, Object> props;
    private Integer order;
    private boolean hidden;
  }

  /** 判断是否为数字类型 */
  public boolean isNumeric() {
    return dataType == DataType.INTEGER
        || dataType == DataType.LONG
        || dataType == DataType.DOUBLE
        || dataType == DataType.DECIMAL;
  }

  /** 判断是否为日期类型 */
  public boolean isDateType() {
    return dataType == DataType.DATE || dataType == DataType.DATETIME || dataType == DataType.TIME;
  }

  /** 判断是否为文本类型 */
  public boolean isTextType() {
    return dataType == DataType.STRING || dataType == DataType.TEXT || dataType == DataType.JSON;
  }

  /** 获取权限元数据（兼容方法） */
  public com.bone.metadata.engine.metadata.FieldLevelSecurityMetadata getPermissionMetadata() {
    // 返回FieldLevelSecurityMetadata实例
    com.bone.metadata.engine.metadata.FieldLevelSecurityMetadata metadata =
        new com.bone.metadata.engine.metadata.FieldLevelSecurityMetadata();
    return metadata;
  }

  /** 获取敏感数据类型（兼容方法） */
  public String getSensitiveDataType() {
    return null;
  }

  /** 获取加密算法（兼容方法） */
  public String getEncryptionAlgorithm() {
    return null;
  }

  /** 是否加密 */
  public boolean isEncrypted() {
    return false;
  }

  /** 是否是计算字段（兼容方法） */
  public boolean isCalculated() {
    return false;
  }

  /** 是否是虚拟字段（兼容方法） */
  public boolean isVirtual() {
    return false;
  }

  /** 是否是显示名称字段 */
  public boolean isDisplayName() {
    return displayName;
  }

  /** 是否是主键字段 */
  public boolean isPrimaryKey() {
    return primaryKey;
  }

  /** 获取API名称 */
  public String getApiName() {
    return this.apiName;
  }

  /** 获取计算表达式 */
  public String getCalculationExpression() {
    return calculationExpression;
  }

  public String getValidationExpression() {
    return validationExpression;
  }

  public void setValidationExpression(String validationExpression) {
    this.validationExpression = validationExpression;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }
}
