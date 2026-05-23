package com.bone.studio.generator.application.query.qry;

public class ListSyncedTablesQuery {
  private String dataSourceId;

  public String getDataSourceId() {
    return dataSourceId;
  }

  public void setDataSourceId(String dataSourceId) {
    this.dataSourceId = dataSourceId;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String dataSourceId;

    public Builder dataSourceId(String dataSourceId) {
      this.dataSourceId = dataSourceId;
      return this;
    }

    public ListSyncedTablesQuery build() {
      ListSyncedTablesQuery qry = new ListSyncedTablesQuery();
      qry.dataSourceId = dataSourceId;
      return qry;
    }
  }
}
