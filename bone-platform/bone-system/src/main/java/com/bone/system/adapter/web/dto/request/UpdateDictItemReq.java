package com.bone.system.adapter.web.dto.request;

import java.time.LocalDateTime;
import lombok.Data;

/** 更新字典项请求（编码不可改，父级走 {@code move} 接口）。 */
@Data
public class UpdateDictItemReq {
  private String label;
  private String value;
  private String tagType;
  private String i18nKey;
  private String externalCode;
  private LocalDateTime effectiveFrom;
  private LocalDateTime effectiveTo;
  private Integer sort;
  private Integer status;
  private String description;
}
