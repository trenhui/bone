package com.bone.metadata.runtime.domain;

import java.util.List;

/**
 * 动作入参声明（由 {@code meta_entity_action_def.params_schema} 的数组元素映射）。
 *
 * <p>前端据 {@code type}/{@code required}/{@code values} 渲染控件并校验必填； 后端 {@code
 * RuntimeActionService.validateParams} 据 {@code required} 做必填校验。
 */
public class ActionParamSpec {

  private final String name;
  private final String type;
  private final boolean required;
  private final List<String> values;
  private final String label;

  public ActionParamSpec(
      String name, String type, boolean required, List<String> values, String label) {
    this.name = name;
    this.type = type;
    this.required = required;
    this.values = values;
    this.label = label;
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
}
