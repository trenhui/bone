package com.bone.studio.generator.infrastructure.service;

public class GeneratorUtils {

  public String toCamelCase(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    StringBuilder result = new StringBuilder();
    boolean capitalizeNext = false;
    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);
      if (c == '_') {
        capitalizeNext = true;
      } else {
        if (capitalizeNext) {
          result.append(Character.toUpperCase(c));
          capitalizeNext = false;
        } else {
          result.append(Character.toLowerCase(c));
        }
      }
    }
    // 首字母大写，用于类名
    if (result.length() > 0) {
      result.setCharAt(0, Character.toUpperCase(result.charAt(0)));
    }
    return result.toString();
  }

  public String toFieldName(String str) {
    String camelCase = toCamelCase(str);
    if (camelCase.length() > 0) {
      return Character.toLowerCase(camelCase.charAt(0)) + camelCase.substring(1);
    }
    return camelCase;
  }

  public String getJavaType(String columnType) {
    // 简单的类型映射，实际项目中可能需要更复杂的映射
    switch (columnType.toLowerCase()) {
      case "varchar":
      case "char":
      case "text":
      case "longtext":
      case "mediumtext":
        return "String";
      case "int":
      case "integer":
      case "smallint":
      case "tinyint":
        return "Integer";
      case "bigint":
        return "Long";
      case "float":
        return "Float";
      case "double":
        return "Double";
      case "decimal":
        return "BigDecimal";
      case "date":
        return "LocalDate";
      case "datetime":
      case "timestamp":
        return "LocalDateTime";
      case "time":
        return "LocalTime";
      case "boolean":
      case "bit":
        return "Boolean";
      default:
        return "String";
    }
  }

  public String getPackagePath(String basePackage, String moduleName) {
    return basePackage + (moduleName != null && !moduleName.isEmpty() ? "." + moduleName : "");
  }
}
