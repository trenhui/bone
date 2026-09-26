package com.bone.system.adapter.web.dto.response;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 字典项响应。
 *
 * <p>{@code parentCode} / {@code level} / {@code path} / {@code hierarchyCode} 是<b>层级视图字段</b>：
 * 同一项在不同层级视图里父级不同，故它们随视图投影，不属于项本身。 {@code children} 仅在树形接口填充。
 */
@Data
@Builder
public class DictItemResp {
  private Long id;
  private Long tenantId;
  private String typeCode;
  private String code;
  private String label;
  private String value;
  private String enumName;
  private String tagType;
  private String i18nKey;
  private String externalCode;
  private LocalDateTime effectiveFrom;
  private LocalDateTime effectiveTo;
  private Integer isDefault;
  private Integer sort;
  private Integer status;
  private String description;

  // ---- 层级视图字段 ----
  private String hierarchyCode;
  private String parentCode;
  private Integer level;
  private String path;
  private boolean hasChildren;
  @Builder.Default private List<DictItemResp> children = new ArrayList<>();

  private Instant createdAt;
  private Instant updatedAt;
}
