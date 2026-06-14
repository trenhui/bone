package com.bone.studio.generator.application.query.qry;

public class DataSourceListQuery {
  private String name;
  private String type;
  private int page;
  private int size;

  private DataSourceListQuery() {}

  public static Builder builder() {
    return new Builder();
  }

  public String getName() {
    return name;
  }

  public String getType() {
    return type;
  }

  public int getPage() {
    return page;
  }

  public int getSize() {
    return size;
  }

  public static class Builder {
    private String name;
    private String type;
    private int page;
    private int size;

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder type(String type) {
      this.type = type;
      return this;
    }

    public Builder page(int page) {
      this.page = page;
      return this;
    }

    public Builder size(int size) {
      this.size = size;
      return this;
    }

    public DataSourceListQuery build() {
      DataSourceListQuery qry = new DataSourceListQuery();
      qry.name = name;
      qry.type = type;
      qry.page = page;
      qry.size = size;
      return qry;
    }
  }
}
