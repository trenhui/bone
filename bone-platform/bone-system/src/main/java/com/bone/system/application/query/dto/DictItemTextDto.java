package com.bone.system.application.query.dto;

import com.bone.system.domain.model.dict.SysDictItemText;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 字典项译文投影（SAP T005T 风格：值语言无关，译文按 (code, language) 存）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictItemTextDto {
  private String typeCode;
  private String code;
  private String language;
  private String label;
  private String description;

  public static DictItemTextDto from(SysDictItemText text) {
    return DictItemTextDto.builder()
        .typeCode(text.getTypeCode())
        .code(text.getCode())
        .language(text.getLanguage())
        .label(text.getLabel())
        .description(text.getDescription())
        .build();
  }
}
