package com.bone.system.domain.model.dict.enums;

import com.bone.core.exception.BizException;
import java.util.Arrays;

/**
 * 字典值的技术类型（SAP <b>Domain</b> 口径：Domain 定技术属性——数据类型、长度、小数位、取值范围； Data Element 定语义；值域定取值范围，三层分工）。
 *
 * <p>把「值必须是什么形状」写进值域定义，而不是散落在每个前端表单里：同一个币种值域在订单页、 报表页、导入模板里的校验口径应当一致。
 */
public enum DictValueType {
  STRING,
  INT,
  DECIMAL,
  BOOLEAN;

  public static DictValueType of(String value) {
    if (value == null || value.isBlank()) {
      return STRING;
    }
    return Arrays.stream(values())
        .filter(t -> t.name().equalsIgnoreCase(value.trim()))
        .findFirst()
        .orElseThrow(() -> BizException.of("字典值类型非法：" + value));
  }

  /** 校验值是否满足该类型，不满足返回 false（由调用方决定转成哪个错误码）。 */
  public boolean matches(String value) {
    if (value == null || value.isBlank()) {
      return true;
    }
    return switch (this) {
      case INT -> value.matches("-?\\d+");
      case DECIMAL -> value.matches("-?\\d+(\\.\\d+)?");
      case BOOLEAN -> "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
      case STRING -> true;
    };
  }
}
