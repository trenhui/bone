package com.bone.system.application.command;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 新建字典项。
 *
 * <p>{@code parentCode} / {@code hierarchyCode} 描述的是<b>层级关系</b>而非项本身的属性： 项落库后由应用服务写入 {@code
 * sys_dict_hierarchy}，不传父级时若是 CASCADE 值域则挂在根。
 */
@Data
public class CreateDictItemCommand {
  private String typeCode;
  private String code;
  private String label;
  private String value;
  private String enumName;
  private String tagType;
  private String i18nKey;

  /** 外部标准码（GB/T 2260 / ISO 4217），仅用于对接。 */
  private String externalCode;

  private LocalDateTime effectiveFrom;
  private LocalDateTime effectiveTo;

  /** 层级视图，缺省 DEFAULT。 */
  private String hierarchyCode;

  private String parentCode;
  private Boolean isDefault;
  private Integer sort;
  private Integer status;
  private String description;
}
