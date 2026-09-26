package com.bone.system.domain.model.dict;

import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.exception.BizException;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.system.domain.model.dict.enums.DictCategory;
import com.bone.system.domain.model.dict.enums.DictValueType;
import com.bone.system.domain.model.dict.valueobject.DictCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 字典类型聚合（定义层）：一个值域的元数据——叫什么、归哪类、真源在哪、谁能改。
 *
 * <p><b>为什么必须和字典项分成两个聚合</b>：v1 把 {@code typeName} 冗余在每一行字典项上，改一行
 * 即与同类型其他行不一致，且「停用某个值域」「给某个值域绑定枚举」这类操作无法表达。类型与项的 生命周期不同（类型改一次，项改很多次），放在一个聚合里只会让每次改项都背上类型的
 * invariants。
 *
 * <p><b>租户语义</b>：{@code tenantId = 0} 为平台级（全租户可见，通常 {@code builtin=1}）； 租户可自建私有类型，但不得删除内置类型。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("sys_dict_type")
public class SysDictType extends TenantAggregateRoot<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private String code;
  private String name;
  private String category;
  private String moduleCode;
  private String enumClass;
  private Integer maxDepth;

  /** 值的技术类型（SAP Domain 口径）：STRING / INT / DECIMAL / BOOLEAN。 */
  private String valueType;

  /** 值格式正则；为空不校验。 */
  private String valueRegex;

  /** 层级编码分段，如 {@code 2,2,2}：非空时可由编码前缀推导父级（GB/T 2260 风格）。 */
  private String codeSegments;

  private String description;

  /** 平台内置：禁删、code 禁改（1=是 0=否）。 */
  private Integer builtin;

  /** 租户是否可改其项（1=可改 0=只读）。 */
  private Integer editable;

  private Integer sort;
  private Integer status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static SysDictType create(
      Long id,
      Long tenantId,
      DictCode code,
      String name,
      DictCategory category,
      String moduleCode,
      String enumClass,
      Integer maxDepth,
      String description,
      boolean builtin,
      Integer sort,
      Integer status) {
    if (name == null || name.isBlank()) {
      throw BizException.of("字典类型名称不能为空");
    }
    if (name.length() > 100) {
      throw BizException.of("字典类型名称长度不能超过 100");
    }
    if (category.isEnum() && (enumClass == null || enumClass.isBlank())) {
      throw BizException.of("ENUM 类字典类型必须绑定枚举类（enumClass）");
    }
    if (!category.isEnum() && enumClass != null && !enumClass.isBlank()) {
      throw BizException.of("仅 ENUM 类字典类型可绑定枚举类");
    }
    int depth = maxDepth == null ? 0 : maxDepth;
    if (depth < 0) {
      throw BizException.of("层级上限不能为负数");
    }
    if (!category.isCascade() && depth > 0) {
      throw BizException.of("仅 CASCADE 类字典类型可设置层级上限");
    }

    SysDictType type = new SysDictType();
    type.id = id;
    type.setTenantId(tenantId == null ? 0L : tenantId);
    type.code = code.value();
    type.name = name.trim();
    type.category = category.name();
    type.moduleCode = moduleCode;
    type.enumClass = category.isEnum() ? enumClass.trim() : null;
    type.maxDepth = depth;
    type.valueType = DictValueType.STRING.name();
    type.description = description;
    type.builtin = builtin ? 1 : 0;
    type.editable = 1;
    type.sort = sort == null ? 0 : sort;
    type.status = status == null ? 1 : status;
    type.createdAt = LocalDateTime.now();
    type.updatedAt = LocalDateTime.now();
    return type;
  }

  /** 更新类型定义。{@code code} 与 {@code builtin} 不可变——编码是跨系统的业务键，改了等于让所有 引用方失效；内置标记是平台护栏，不能被运营态操作摘掉。 */
  public void update(
      String name,
      String moduleCode,
      String enumClass,
      Integer maxDepth,
      String description,
      Integer sort,
      Integer status) {
    DictCategory cat = category();
    if (name == null || name.isBlank()) {
      throw BizException.of("字典类型名称不能为空");
    }
    if (cat.isEnum() && (enumClass == null || enumClass.isBlank())) {
      throw BizException.of("ENUM 类字典类型必须绑定枚举类（enumClass）");
    }
    int depth = maxDepth == null ? 0 : maxDepth;
    if (depth < 0) {
      throw BizException.of("层级上限不能为负数");
    }
    if (!cat.isCascade() && depth > 0) {
      throw BizException.of("仅 CASCADE 类字典类型可设置层级上限");
    }
    this.name = name.trim();
    this.moduleCode = moduleCode;
    this.enumClass = cat.isEnum() ? enumClass.trim() : null;
    this.maxDepth = depth;
    this.description = description;
    this.sort = sort == null ? this.sort : sort;
    this.status = status == null ? this.status : status;
    this.updatedAt = LocalDateTime.now();
  }

  /** 只更新运营态字段（名称、排序、启停、说明），不动真源配置（枚举类、层级上限）。 */
  public void updateOperational(String name, Integer sort, Integer status, String description) {
    if (name != null && !name.isBlank()) {
      this.name = name.trim();
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

  /**
   * 设定值的技术属性（SAP Domain 的职责：数据类型 + 格式约束）与编码分段规则。
   *
   * @param valueType 为空默认 {@link DictValueType#STRING}
   * @param valueRegex 为空表示不校验格式
   * @param codeSegments 形如 {@code 2,2,2}；非法格式直接拒绝——宁可建值时报错， 也比在导入时静默推导出错父子关系更容易排查
   */
  public void applyValueFormat(String valueType, String valueRegex, String codeSegments) {
    this.valueType = DictValueType.of(valueType).name();
    this.valueRegex = valueRegex == null || valueRegex.isBlank() ? null : valueRegex.trim();
    this.codeSegments = codeSegments == null || codeSegments.isBlank() ? null : codeSegments.trim();
    if (this.codeSegments != null) {
      segments();
    }
    this.updatedAt = LocalDateTime.now();
  }

  /**
   * 校验值是否符合本值域定义（类型 + 正则）。
   *
   * @throws BizException 不符合时抛出，由应用服务转成 {@code SYS_DICT_VALUE_INVALID}
   */
  public void assertValueFormat(String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    DictValueType type = DictValueType.of(valueType);
    if (!type.matches(value)) {
      throw BizException.of("值不符合值域类型 " + type.name() + "：" + value);
    }
    if (valueRegex != null && !value.matches(valueRegex)) {
      throw BizException.of("值不匹配值域格式 " + valueRegex + "：" + value);
    }
  }

  /** 编码分段（如 {@code 2,2,2}）；未配置返回空列表。 */
  public List<Integer> segments() {
    if (codeSegments == null || codeSegments.isBlank()) {
      return List.of();
    }
    List<Integer> result = new ArrayList<>();
    for (String part : codeSegments.split(",")) {
      String trimmed = part.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      try {
        int len = Integer.parseInt(trimmed);
        if (len <= 0) {
          throw BizException.of("编码分段必须为正整数：" + codeSegments);
        }
        result.add(len);
      } catch (NumberFormatException ex) {
        throw BizException.of("编码分段格式非法（应为逗号分隔的长度，如 2,2,2）：" + codeSegments);
      }
    }
    return result;
  }

  /**
   * 按编码前缀推导父级编码（GB/T 2260 / UNSPSC / ICD-10 风格）。
   *
   * <p>例如分段 {@code 2,2,2}、编码 {@code 440100} → 父 {@code 440000}（末段置零）。
   * 结果只是<b>建议</b>：父编码不存在时由调用方决定是挂到根还是标记为待补，不阻断导入。
   */
  public Optional<String> deriveParentCode(String itemCode) {
    List<Integer> segs = segments();
    if (segs.isEmpty() || itemCode == null) {
      return Optional.empty();
    }
    int total = segs.stream().mapToInt(Integer::intValue).sum();
    if (itemCode.length() != total) {
      return Optional.empty();
    }
    List<String> parts = new ArrayList<>();
    int offset = 0;
    for (int len : segs) {
      parts.add(itemCode.substring(offset, offset + len));
      offset += len;
    }
    // 末段为 0 表示「第 k 级代码」：父级 = 保留前 k-1 段、其后全部置零。
    // 440100（广州市 44|01|00）→ 440000（广东省）；440305（南山区 44|03|05）→ 440300（深圳市）。
    int lastNonZero = -1;
    for (int i = 0; i < parts.size(); i++) {
      if (!isAllZero(parts.get(i))) {
        lastNonZero = i;
      }
    }
    if (lastNonZero <= 0) {
      return Optional.empty();
    }
    StringBuilder parent = new StringBuilder();
    for (int i = 0; i < parts.size(); i++) {
      parent.append(i < lastNonZero ? parts.get(i) : "0".repeat(segs.get(i)));
    }
    return Optional.of(parent.toString());
  }

  private static boolean isAllZero(String segment) {
    return segment.chars().allMatch(c -> c == '0');
  }

  /** 删除前置校验：内置类型是平台护栏，运营态不可删。 */
  public void assertDeletable() {
    if (isBuiltin()) {
      throw BizException.of("内置字典类型不可删除：" + code);
    }
  }

  /** 租户改项前置校验：{@code editable=0} 的值域由平台统一口径，租户只读。 */
  public void assertEditableByTenant() {
    if (editable != null && editable == 0) {
      throw BizException.of("该字典类型的项由平台统一维护，租户只读：" + code);
    }
  }

  public boolean isBuiltin() {
    return builtin != null && builtin == 1;
  }

  public boolean isEnabled() {
    return status != null && status == 1;
  }

  public DictCategory category() {
    return DictCategory.of(category);
  }

  public DictCode code() {
    return DictCode.typeCode(code);
  }
}
