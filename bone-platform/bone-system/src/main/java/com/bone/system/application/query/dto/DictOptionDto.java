package com.bone.system.application.query.dto;

import com.bone.system.domain.model.dict.SysDictItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 下拉数据源投影：只带「渲染一个选项」需要的字段。
 *
 * <p>为什么不给前端吐完整字典项：{@code /options} 是全平台共用入口，返回体越小、字段越稳定， 越不容易被某个页面悄悄依赖上内部字段后反过来绑架字典模型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictOptionDto {
  private String code;
  private String label;
  private String value;
  private String tagType;
  private boolean isDefault;
  private Integer sort;

  public static DictOptionDto from(SysDictItem item) {
    return DictOptionDto.builder()
        .code(item.getCode())
        .label(item.getLabel())
        .value(item.getValue())
        .tagType(item.getTagType())
        .isDefault(item.isDefaultItem())
        .sort(item.getSort())
        .build();
  }
}
