package com.bone.system.domain.model.dict;

import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.exception.BizException;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.system.domain.model.dict.valueobject.DictCode;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 字典项译文聚合（SAP {@code T005T} 文本表 / Oracle {@code FND_LOOKUP_VALUES_TL} 风格）。
 *
 * <p><b>为什么不把译文塞进字典项</b>：值是语言无关的（{@code code} 才是契约），译文是一对多的 附属数据。放进主表要么开 {@code label_zh}/{@code
 * label_en} 列（加一种语言改一次表）， 要么塞 JSON（无法按语言查询、无法被 DB 约束）。副表按 {@code (code, language)} 唯一，
 * 加语言就是加行——运营可改，不依赖前端发版。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("sys_dict_item_text")
public class SysDictItemText extends TenantAggregateRoot<Long> {

  private static final int MAX_LANGUAGE_LENGTH = 16;

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private String typeCode;
  private String code;
  private String language;
  private String label;
  private String description;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static SysDictItemText create(
      Long id,
      Long tenantId,
      DictCode typeCode,
      DictCode code,
      String language,
      String label,
      String description) {
    if (language == null || language.isBlank()) {
      throw BizException.of("语言标签不能为空");
    }
    if (language.length() > MAX_LANGUAGE_LENGTH) {
      throw BizException.of("语言标签长度不能超过 " + MAX_LANGUAGE_LENGTH);
    }
    if (label == null || label.isBlank()) {
      throw BizException.of("译文显示名不能为空");
    }
    SysDictItemText text = new SysDictItemText();
    text.id = id;
    text.setTenantId(tenantId == null ? 0L : tenantId);
    text.typeCode = typeCode.value();
    text.code = code.value();
    text.language = language.trim();
    text.label = label.trim();
    text.description = description;
    text.createdAt = LocalDateTime.now();
    text.updatedAt = LocalDateTime.now();
    return text;
  }

  public void update(String label, String description) {
    if (label != null && !label.isBlank()) {
      if (label.length() > 100) {
        throw BizException.of("译文显示名长度不能超过 100");
      }
      this.label = label.trim();
    }
    if (description != null) {
      this.description = description;
    }
    this.updatedAt = LocalDateTime.now();
  }
}
