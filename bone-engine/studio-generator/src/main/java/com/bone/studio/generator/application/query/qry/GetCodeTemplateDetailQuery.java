package com.bone.studio.generator.application.query.qry;

/** 模板详情查询：与列表查询同源，供「模板管理」详情页按 id 读取。 */
public class GetCodeTemplateDetailQuery {
  private Long id;

  public static Builder builder() {
    return new Builder();
  }

  public Long getId() {
    return id;
  }

  public static class Builder {
    private Long id;

    public Builder id(Long id) {
      this.id = id;
      return this;
    }

    public GetCodeTemplateDetailQuery build() {
      GetCodeTemplateDetailQuery qry = new GetCodeTemplateDetailQuery();
      qry.id = id;
      return qry;
    }
  }
}
