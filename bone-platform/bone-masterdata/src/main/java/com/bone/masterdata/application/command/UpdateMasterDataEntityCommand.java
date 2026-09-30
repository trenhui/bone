package com.bone.masterdata.application.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMasterDataEntityCommand {
  private Long id;
  private String name;
  private String description;
  private String category;

  /** 实体编码（可空=不改）。 */
  private String entityCode;
}
