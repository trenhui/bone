package com.bone.metadata.runtime.domain;

import java.util.List;

/**
 * 动作入参声明（由 {@code meta_entity_action_def.params_schema} 的数组元素映射）。
 *
 * <p>前端据 {@code type}/{@code required}/{@code values} 渲染控件并校验必填； 后端 {@code
 * RuntimeActionApplicationService.validateParams} 据 {@code type}/{@code min}/{@code max}/{@code
 * pattern}/ {@code values} 做类型与约束校验（Hasura Actions inputSchema 语义：平台统一校验入参， Handler 不再各自校验）。
 *
 * <p>支持类型：{@code string} / {@code number} / {@code integer} / {@code boolean} / {@code enum}；
 * 约束：{@code min}（number/integer 最小值）、{@code max}（number/integer 最大值）、 {@code pattern}（string
 * 正则）、{@code values}（enum 允许值）。
 */
public class ActionParamSpec {

  private final String name;
  private final String type;
  private final boolean required;
  private final List<String> values;
  private final String label;
  private final Double min;
  private final Double max;
  private final String pattern;

  public ActionParamSpec(
      String name,
      String type,
      boolean required,
      List<String> values,
      String label,
      Double min,
      Double max,
      String pattern) {
    this.name = name;
    this.type = type;
    this.required = required;
    this.values = values;
    this.label = label;
    this.min = min;
    this.max = max;
    this.pattern = pattern;
  }

  public ActionParamSpec(
      String name, String type, boolean required, List<String> values, String label) {
    this(name, type, required, values, label, null, null, null);
  }

  public String getName() {
    return name;
  }

  public String getType() {
    return type;
  }

  public boolean isRequired() {
    return required;
  }

  public List<String> getValues() {
    return values;
  }

  public String getLabel() {
    return label;
  }

  public Double getMin() {
    return min;
  }

  public Double getMax() {
    return max;
  }

  public String getPattern() {
    return pattern;
  }
}
