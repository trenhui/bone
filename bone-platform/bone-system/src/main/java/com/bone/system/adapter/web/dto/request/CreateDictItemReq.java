package com.bone.system.adapter.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.Data;

/** 新建字典项请求。{@code parentCode} / {@code hierarchyCode} 描述层级关系，不落在项本身。 */
@Data
public class CreateDictItemReq {

  @NotBlank(message = "所属值域编码不能为空")
  private String typeCode;

  @NotBlank(message = "字典项编码不能为空")
  private String code;

  @NotBlank(message = "字典项显示名不能为空")
  private String label;

  private String value;
  private String enumName;
  private String tagType;
  private String i18nKey;

  /** 外部标准码（GB/T 2260 / ISO 4217）。 */
  private String externalCode;

  /** 生效区间（ISO-8601）；为空表示不限。 */
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
