package com.bone.system.domain.model.dict.valueobject;

import com.bone.core.exception.BizException;

/**
 * 字典编码值对象：类型编码与项编码共用的不变量（非空、无空白、长度上限）。
 *
 * <p><b>为什么长度上限要参数化</b>：类型编码（{@code sys_dict_type.code}）落库列宽 64，项编码 （{@code
 * sys_dict_item.code}）落库列宽 100。把两个上限塞进两个类会产出两份几乎相同的校验逻辑， 而只留一个「取大者」的宽松上限又会让超长类型编码穿透到 DB
 * 报错——那才是真正的坏味道（列宽约束 描述了两次却不一致）。故同一个值对象带上限参数，由工厂方法给出语义化入口。
 *
 * <p><b>禁止空白字符</b>：字典编码会被拼进 i18n key、导入导出 JSON、前端 option value， 内含空格或换行会让这些消费点在无提示的情况下静默错位。
 */
public record DictCode(String value, int maxLength) {

  public DictCode {
    if (value == null || value.isBlank()) {
      throw BizException.of("字典编码不能为空");
    }
    if (value.length() > maxLength) {
      throw BizException.of("字典编码长度不能超过 " + maxLength);
    }
    if (value.chars().anyMatch(Character::isWhitespace)) {
      throw BizException.of("字典编码不能包含空白字符");
    }
  }

  /** 字典项编码（列宽 100）。 */
  public static DictCode of(String value) {
    return new DictCode(value, 100);
  }

  /** 字典类型编码（列宽 64）。 */
  public static DictCode typeCode(String value) {
    return new DictCode(value, 64);
  }

  @Override
  public String toString() {
    return value;
  }
}
