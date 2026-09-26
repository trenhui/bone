package com.bone.system.application.command;

import java.time.LocalDateTime;
import lombok.Data;

/** 更新字典项；{@code code} / {@code typeCode} 是业务键，父级走 {@code move} 用例。 */
@Data
public class UpdateDictItemCommand {
  private Long id;
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
