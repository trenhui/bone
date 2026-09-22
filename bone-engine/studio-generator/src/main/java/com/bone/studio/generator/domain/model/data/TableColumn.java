package com.bone.studio.generator.domain.model.data;

public class TableColumn {
  private String columnName;
  private int jdbcType;
  private String columnType;
  private String columnComment;
  private boolean nullable;
  private boolean primaryKey;
  private String defaultValue;
  private int columnSize;
  private int decimalDigits;
  private int length;
  private int precision;
  private int scale;

  private TableColumn() {}

  public static Builder builder() {
    return new Builder();
  }

  public String getColumnName() {
    return columnName;
  }

  public int getJdbcType() {
    return jdbcType;
  }

  public String getColumnType() {
    return columnType;
  }

  public String getColumnComment() {
    return columnComment;
  }

  public boolean isNullable() {
    return nullable;
  }

  public boolean isPrimaryKey() {
    return primaryKey;
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  public int getColumnSize() {
    return columnSize;
  }

  public int getDecimalDigits() {
    return decimalDigits;
  }

  public int getLength() {
    return length;
  }

  public int getPrecision() {
    return precision;
  }

  public int getScale() {
    return scale;
  }

  public static class Builder {
    private String columnName;
    private int jdbcType;
    private String columnType;
    private String columnComment;
    private boolean nullable;
    private boolean primaryKey;
    private String defaultValue;
    private int columnSize;
    private int decimalDigits;
    private int length;
    private int precision;
    private int scale;

    public Builder columnName(String columnName) {
      this.columnName = columnName;
      return this;
    }

    public Builder jdbcType(int jdbcType) {
      this.jdbcType = jdbcType;
      return this;
    }

    public Builder columnType(String columnType) {
      this.columnType = columnType;
      return this;
    }

    public Builder columnSize(int columnSize) {
      this.columnSize = columnSize;
      return this;
    }

    public Builder decimalDigits(int decimalDigits) {
      this.decimalDigits = decimalDigits;
      return this;
    }

    public Builder columnComment(String columnComment) {
      this.columnComment = columnComment;
      return this;
    }

    public Builder nullable(boolean nullable) {
      this.nullable = nullable;
      return this;
    }

    public Builder primaryKey(boolean primaryKey) {
      this.primaryKey = primaryKey;
      return this;
    }

    public Builder defaultValue(String defaultValue) {
      this.defaultValue = defaultValue;
      return this;
    }

    public Builder length(int length) {
      this.length = length;
      return this;
    }

    public Builder precision(int precision) {
      this.precision = precision;
      return this;
    }

    public Builder scale(int scale) {
      this.scale = scale;
      return this;
    }

    public Builder remarks(String remarks) {
      this.columnComment = remarks;
      return this;
    }

    public TableColumn build() {
      TableColumn column = new TableColumn();
      column.columnName = columnName;
      column.jdbcType = jdbcType;
      column.columnType = columnType;
      column.columnComment = columnComment;
      column.nullable = nullable;
      column.primaryKey = primaryKey;
      column.defaultValue = defaultValue;
      column.columnSize = columnSize;
      column.decimalDigits = decimalDigits;
      column.length = length;
      column.precision = precision;
      column.scale = scale;
      return column;
    }
  }
}
