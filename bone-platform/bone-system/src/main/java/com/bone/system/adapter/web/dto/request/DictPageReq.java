package com.bone.system.adapter.web.dto.request;

import lombok.Data;

/**
 * 字典分页查询请求（adapter 协议对象，E-13.1）。
 *
 * <p>与 application 的 {@code DictPageQuery} 同名但不同层：这里的约束是 HTTP 查询参数（含默认值与绑定语义）， application
 * 那份是用例入参。让 Controller 直接绑定 application 的 Query 会把 HTTP 参数默认值写进应用层——协议一改就穿透到用例。
 */
@Data
public class DictPageReq {
  private String type;
  private String keyword;
  private int pageNum = 1;
  private int pageSize = 10;
}
