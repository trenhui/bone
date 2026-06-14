package com.bone.studio.generator.application.query.qry;

public class DataSourceByIdQuery {
  private String id;

  private DataSourceByIdQuery() {}

  public static Builder builder() {
    return new Builder();
  }

  public String getId() {
    return id;
  }

  public static class Builder {
    private String id;

    public Builder id(String id) {
      this.id = id;
      return this;
    }

    public DataSourceByIdQuery build() {
      DataSourceByIdQuery qry = new DataSourceByIdQuery();
      qry.id = id;
      return qry;
    }
  }
}
