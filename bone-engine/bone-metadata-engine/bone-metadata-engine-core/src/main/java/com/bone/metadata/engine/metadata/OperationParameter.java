package com.bone.metadata.engine.metadata;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 操作参数定义 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationParameter {
  private String name;

  private String label;

  private FieldType type;

  @Builder.Default private boolean required = false;

  private Object defaultValue;
  private String description;

  @Builder.Default private List<String> allowedValues = new ArrayList<>();

  public enum FieldType {
    STRING,
    INTEGER,
    LONG,
    DOUBLE,
    BOOLEAN,
    DATE,
    DATETIME,
    OBJECT,
    ARRAY,
    ENUM,
    REFERENCE
  }
}
