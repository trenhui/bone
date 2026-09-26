package com.bone.system.domain.model.dict;

import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.exception.BizException;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.system.domain.model.dict.enums.DictTagType;
import com.bone.system.domain.model.dict.valueobject.DictCode;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 字典项聚合（值层）——<b>扁平，不含父指针</b>。
 *
 * <p><b>为什么父子关系不落在这里</b>：父指针会把「层级」焊死成值的固有属性，于是一个值只能有一个父、 一个值域只能有一棵树。真实场景里同一批值需要多套层级视图（组织架构树 /
 * 报表汇总树）， 且一个值可以属于多个父（广州既属于广东省、也属于一线城市）。SAP 的做法是值表扁平、 层级放独立对象（HANA hierarchy 表 / {@code
 * SETHEADER}·{@code SETLEAF}），SKOS 用 {@code skos:broader} 表达可多套的概念关系——层级是<b>关系</b>，不是<b>属性</b>。
 *
 * <p><b>code 与 value 为什么要分开</b>：{@code code} 是稳定业务键（枚举名、区域码）， {@code value} 是对外序列化值。再叠加 {@code
 * externalCode} 承载外部标准码（GB/T 2260、 ISO 4217）——内部码是平台契约、外部码是对接契约，两者混用正是 v1 的坑。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("sys_dict_item")
public class SysDictItem extends TenantAggregateRoot<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private String typeCode;
  private String code;
  private String label;
  private String value;
  private String enumName;
  private String tagType;
  private String i18nKey;

  /** 外部标准码，仅用于对接（不参与平台内部引用）。 */
  private String externalCode;

  /** 生效开始时间（Oracle 时间有效性）；为空表示不限。 */
  private LocalDateTime effectiveFrom;

  /** 生效结束时间；为空表示不限。 */
  private LocalDateTime effectiveTo;

  /** 是否该类型默认项（1=是 0=否，同租户同类型唯一）。 */
  private Integer isDefault;

  private Integer sort;
  private Integer status;
  private String description;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static SysDictItem create(
      Long id,
      Long tenantId,
      DictCode typeCode,
      DictCode code,
      String label,
      String value,
      String enumName,
      DictTagType tagType,
      String i18nKey,
      String externalCode,
      LocalDateTime effectiveFrom,
      LocalDateTime effectiveTo,
      boolean isDefault,
      Integer sort,
      Integer status,
      String description) {
    if (label == null || label.isBlank()) {
      throw BizException.of("字典项显示名不能为空");
    }
    if (label.length() > 100) {
      throw BizException.of("字典项显示名长度不能超过 100");
    }
    assertRange(effectiveFrom, effectiveTo);
    SysDictItem item = new SysDictItem();
    item.id = id;
    item.setTenantId(tenantId == null ? 0L : tenantId);
    item.typeCode = typeCode.value();
    item.code = code.value();
    item.label = label.trim();
    item.value = value;
    item.enumName = enumName;
    item.tagType = (tagType == null ? DictTagType.DEFAULT : tagType).code();
    item.i18nKey = i18nKey;
    item.externalCode = externalCode;
    item.effectiveFrom = effectiveFrom;
    item.effectiveTo = effectiveTo;
    item.isDefault = isDefault ? 1 : 0;
    item.sort = sort == null ? 0 : sort;
    item.status = status == null ? 1 : status;
    item.description = description;
    item.createdAt = LocalDateTime.now();
    item.updatedAt = LocalDateTime.now();
    return item;
  }

  /** 更新运营态字段；{@code code} / {@code typeCode} 是业务键，创建后不可改。 */
  public void update(
      String label,
      String value,
      DictTagType tagType,
      String i18nKey,
      String externalCode,
      LocalDateTime effectiveFrom,
      LocalDateTime effectiveTo,
      Integer sort,
      Integer status,
      String description) {
    if (label != null && !label.isBlank()) {
      if (label.length() > 100) {
        throw BizException.of("字典项显示名长度不能超过 100");
      }
      this.label = label.trim();
    }
    if (value != null) {
      this.value = value;
    }
    if (tagType != null) {
      this.tagType = tagType.code();
    }
    if (i18nKey != null) {
      this.i18nKey = i18nKey;
    }
    if (externalCode != null) {
      this.externalCode = externalCode;
    }
    if (effectiveFrom != null || effectiveTo != null) {
      LocalDateTime from = effectiveFrom == null ? this.effectiveFrom : effectiveFrom;
      LocalDateTime to = effectiveTo == null ? this.effectiveTo : effectiveTo;
      assertRange(from, to);
      this.effectiveFrom = from;
      this.effectiveTo = to;
    }
    if (sort != null) {
      this.sort = sort;
    }
    if (status != null) {
      this.status = status;
    }
    if (description != null) {
      this.description = description;
    }
    this.updatedAt = LocalDateTime.now();
  }

  /** 指定时刻是否落在生效区间内（两端为空表示不限）。 */
  public boolean isEffectiveAt(LocalDateTime at) {
    LocalDateTime now = at == null ? LocalDateTime.now() : at;
    return (effectiveFrom == null || !now.isBefore(effectiveFrom))
        && (effectiveTo == null || !now.isAfter(effectiveTo));
  }

  /** 当前时刻是否生效——读侧（下拉、树、分页）的默认过滤口径。 */
  public boolean isEffectiveNow() {
    return isEffectiveAt(LocalDateTime.now());
  }

  /** 已过期（结束时间早于当前）。 */
  public boolean isExpired(LocalDateTime at) {
    return effectiveTo != null && effectiveTo.isBefore(at == null ? LocalDateTime.now() : at);
  }

  /** 尚未生效。 */
  public boolean isPending(LocalDateTime at) {
    return effectiveFrom != null && effectiveFrom.isAfter(at == null ? LocalDateTime.now() : at);
  }

  public boolean isDefaultItem() {
    return isDefault != null && isDefault == 1;
  }

  public boolean isEnabled() {
    return status != null && status == 1;
  }

  public DictTagType tagType() {
    return DictTagType.ofCode(tagType);
  }

  public DictCode code() {
    return DictCode.of(code);
  }

  public void markAsDefault() {
    this.isDefault = 1;
    this.updatedAt = LocalDateTime.now();
  }

  public void clearDefault() {
    this.isDefault = 0;
    this.updatedAt = LocalDateTime.now();
  }

  private static void assertRange(LocalDateTime from, LocalDateTime to) {
    if (from != null && to != null && from.isAfter(to)) {
      throw BizException.of("生效开始时间不能晚于结束时间");
    }
  }
}
