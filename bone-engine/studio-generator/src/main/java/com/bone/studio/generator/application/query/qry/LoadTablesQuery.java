package com.bone.studio.generator.application.query.qry;

public class LoadTablesQuery {
  private String dataSourceId;

  /** 表名/注释关键字（大小写不敏感）；空=不过滤。 */
  private String keyword;

  /** 返回条数上限，防止真实库上千张表一次性回传（默认 200）。 */
  private Integer limit;

  /** 是否回传列明细；表选择器只需要表名，关掉可把报文降一个数量级（默认 true，保持既有契约）。 */
  private Boolean includeColumns;

  private LoadTablesQuery() {}

  public static Builder builder() {
    return new Builder();
  }

  public String getDataSourceId() {
    return dataSourceId;
  }

  public String getKeyword() {
    return keyword;
  }

  public Integer getLimit() {
    return limit;
  }

  public Boolean getIncludeColumns() {
    return includeColumns;
  }

  public static class Builder {
    private String dataSourceId;
    private String keyword;
    private Integer limit;
    private Boolean includeColumns;

    public Builder dataSourceId(String dataSourceId) {
      this.dataSourceId = dataSourceId;
      return this;
    }

    public Builder keyword(String keyword) {
      this.keyword = keyword;
      return this;
    }

    public Builder limit(Integer limit) {
      this.limit = limit;
      return this;
    }

    public Builder includeColumns(Boolean includeColumns) {
      this.includeColumns = includeColumns;
      return this;
    }

    public LoadTablesQuery build() {
      LoadTablesQuery qry = new LoadTablesQuery();
      qry.dataSourceId = dataSourceId;
      qry.keyword = keyword;
      qry.limit = limit;
      qry.includeColumns = includeColumns;
      return qry;
    }
  }
}
