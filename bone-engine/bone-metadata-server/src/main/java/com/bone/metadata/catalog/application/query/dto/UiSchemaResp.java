package com.bone.metadata.catalog.application.query.dto;

import lombok.Data;

/**
 * 团队级界面 schema 响应（迁移 0046）。
 *
 * <p>{@code data=null} = 从未配置，前端回落列投影（与 ui-overlay 同语义）。
 */
@Data
public class UiSchemaResp {

  /** BoneUiSchema 全量 JSON（草稿）。 */
  private String json;

  /** 协议增量版本（版本协商：未知 minor 只告警不拒绝）。 */
  private Integer minor;

  /** 乐观锁版本（PUT 回传 {@code If-Match: <lockVersion>}，响应 ETag 同值）。 */
  private Long lockVersion;

  private String updatedAt;
}
