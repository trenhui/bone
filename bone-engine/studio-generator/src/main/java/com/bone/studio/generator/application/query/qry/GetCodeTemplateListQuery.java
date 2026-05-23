package com.bone.studio.generator.application.query.qry;

public class GetCodeTemplateListQuery {
  private Integer page;
  private Integer size;
  private String type;
  private String status;

  public static Builder builder() {
    return new Builder();
  }

  public Integer getPage() {
    return page;
  }

  public Integer getSize() {
    return size;
  }

  public String getType() {
    return type;
  }

  public String getStatus() {
    return status;
  }

  public static class Builder {
    private Integer page;
    private Integer size;
    private String type;
    private String status;

    public Builder page(Integer page) {
      this.page = page;
      return this;
    }

    public Builder size(Integer size) {
      this.size = size;
      return this;
    }

    public Builder type(String type) {
      this.type = type;
      return this;
    }

    public Builder status(String status) {
      this.status = status;
      return this;
    }

    public GetCodeTemplateListQuery build() {
      GetCodeTemplateListQuery qry = new GetCodeTemplateListQuery();
      qry.page = page;
      qry.size = size;
      qry.type = type;
      qry.status = status;
      return qry;
    }
  }
}
