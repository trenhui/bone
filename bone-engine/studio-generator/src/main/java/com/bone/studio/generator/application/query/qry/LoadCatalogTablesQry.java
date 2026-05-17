package com.bone.studio.generator.application.query.qry;

import java.util.List;

/** 对齐 Bone-API §2.5：page/size 从 1 起。 */
public class LoadCatalogTablesQry {
  private int page = 1;
  private int size = 20;
  private Long tenantId;
  private List<String> entityCodes;
  private String keyword;

  public int getPage() {
    return page;
  }

  public void setPage(int page) {
    this.page = page;
  }

  public int getSize() {
    return size;
  }

  public void setSize(int size) {
    this.size = size;
  }

  public Long getTenantId() {
    return tenantId;
  }

  public void setTenantId(Long tenantId) {
    this.tenantId = tenantId;
  }

  public List<String> getEntityCodes() {
    return entityCodes;
  }

  public void setEntityCodes(List<String> entityCodes) {
    this.entityCodes = entityCodes;
  }

  public String getKeyword() {
    return keyword;
  }

  public void setKeyword(String keyword) {
    this.keyword = keyword;
  }
}
