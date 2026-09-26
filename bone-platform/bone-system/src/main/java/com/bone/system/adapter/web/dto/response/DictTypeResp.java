package com.bone.system.adapter.web.dto.response;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

/** 字典类型响应。{@code builtin=1} 的类型前端应置灰删除与编码编辑。 */
@Data
@Builder
public class DictTypeResp {
  private Long id;
  private Long tenantId;
  private String code;
  private String name;
  private String category;
  private String moduleCode;
  private String enumClass;
  private Integer maxDepth;

  /** 值的技术类型（SAP Domain 口径）。 */
  private String valueType;

  private String valueRegex;

  /** 层级编码分段，如 2,2,2。 */
  private String codeSegments;

  private String description;
  private Integer builtin;
  private Integer editable;
  private Integer sort;
  private Integer status;
  private Instant createdAt;
  private Instant updatedAt;
}
