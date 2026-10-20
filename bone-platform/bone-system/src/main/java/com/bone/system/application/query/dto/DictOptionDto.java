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

  /**
   * 生命周期状态（v1.1 三态）：ACTIVE / DEPRECATED；DISABLED 不进 /options。
   *
   * <p>一个端点两种口径的载体：渲染消费方按 {@code ACTIVE} 过滤（新表单不出现 DEPRECATED）， 写校验消费方（选项源物化）接受 ACTIVE +
   * DEPRECATED（存量写放行）。见 doc/design/lowcode/ui/界面设计器字典打通设计方案.md §4.2/§4.5。
   */
  private String state;

  public static DictOptionDto from(SysDictItem item) {
    return DictOptionDto.builder()
        .code(item.getCode())
        .label(item.getLabel())
        .value(item.getValue())
        .tagType(item.getTagType())
        .isDefault(item.isDefaultItem())
        .sort(item.getSort())
        .state(item.stateName())
        .build();
  }
}
