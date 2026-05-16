package com.bone.metadata.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Smart field metadata class Extends from base field metadata, providing advanced field features
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class SmartFieldMetadata extends FieldMetadata {
  // Field calculation expression
  private String calculationExpression;

  // Default value expression
  private String defaultValueExpression;

  // Field formatting template
  private String formatPattern;

  // Field validation expression
  private String validationExpression;

  // Whether the field is virtual (not stored in database)
  @Builder.Default private boolean virtual = false;

  // Whether the field is calculated
  @Builder.Default private boolean calculated = false;

  // Whether the field is read-only
  @Builder.Default private boolean readonly = false;

  // Field data dictionary
  private String dataDictionaryCode;

  // Business rule associated with the field
  private String businessRuleCode;

  // Conditional display rule for the field
  private String conditionalDisplayRule;

  @Override
  public String getCalculationExpression() {
    return this.calculationExpression;
  }

  /** Checks if the field is virtual */
  @Override
  public boolean isVirtual() {
    return this.virtual;
  }

  /** Checks if the field is calculated */
  @Override
  public boolean isCalculated() {
    return this.calculated;
  }

  /** Gets the display value of the field */
  public Object getDisplayValue(Object rawValue) {
    // Simple implementation, may require more complex formatting logic in practice
    if (formatPattern != null && rawValue != null) {
      // Formatting logic can be implemented here
      return String.format(formatPattern, rawValue);
    }
    return rawValue;
  }
}
