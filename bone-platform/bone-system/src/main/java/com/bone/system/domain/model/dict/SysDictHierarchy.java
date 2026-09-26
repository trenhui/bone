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
 * 字典层级关系聚合：把「谁是谁的父」从字典项里拆出来，成为可插拔的关系。
 *
 * <p><b>为什么值得单独一张表</b>（对应 SAP 值表扁平 + 独立 hierarchy 对象、SKOS {@code skos:broader}）：
 *
 * <ul>
 *   <li>同一值域可并存多套层级视图（{@code hierarchyCode}：DEFAULT / 报表树 / …）；
 *   <li>层级变更不再触碰值本身——改挂载不影响 {@code code/label/value}，两者并发域分离；
 *   <li>值表保持扁平，最高频的下拉查询仍是 {@code WHERE type_code = ?} 单列等值。
 * </ul>
 *
 * <p><b>path 与 level 为什么冗余</b>：读子树是 {@code path LIKE '/GD/%'} 一次前缀匹配， 不必递归或写 CTE；{@code level} 直接供
 * {@code maxDepth} 校验与前端缩进，不必回放链路。 冗余换的是读侧复杂度——字典是读多写少的数据，这笔交易划算。
 *
 * <p><b>唯一键 {@code (tenant_id, type_code, hierarchy_code, code)} 保证同一视图内是树不是图</b>——
 * 一个值在同一套层级里只能有一个父，环路在写入侧即被拒绝。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("sys_dict_hierarchy")
public class SysDictHierarchy extends TenantAggregateRoot<Long> {

  /** 默认层级视图：不传 {@code hierarchyCode} 时使用，行为与「内联父指针」完全一致。 */
  public static final String DEFAULT_HIERARCHY = "DEFAULT";

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private String typeCode;
  private String hierarchyCode;
  private String code;
  private String parentCode;
  private String path;
  private Integer level;
  private Integer sort;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static SysDictHierarchy create(
      Long id,
      Long tenantId,
      DictCode typeCode,
      String hierarchyCode,
      DictCode code,
      String parentCode,
      Integer parentLevel,
      String parentPath,
      Integer sort) {
    String hierarchy = normalizeHierarchyCode(hierarchyCode);
    int level = parentLevel == null ? 1 : parentLevel + 1;
    String path = pathOf(parentPath, code.value());
    SysDictHierarchy node = new SysDictHierarchy();
    node.id = id;
    node.setTenantId(tenantId == null ? 0L : tenantId);
    node.typeCode = typeCode.value();
    node.hierarchyCode = hierarchy;
    node.code = code.value();
    node.parentCode = parentCode;
    node.path = path;
    node.level = level;
    node.sort = sort == null ? 0 : sort;
    node.createdAt = LocalDateTime.now();
    node.updatedAt = LocalDateTime.now();
    return node;
  }

  /**
   * 物化路径：根为 {@code /CODE/}，子节点为 {@code 父路径 + CODE/}。
   *
   * <p>前后都带分隔符，使「包含判断」可以用 {@code /X/} 精确匹配，避免 {@code /GZ} 命中 {@code /GZX/}。
   */
  public static String pathOf(String parentPath, String code) {
    String base = parentPath == null || parentPath.isBlank() ? "/" : parentPath;
    if (!base.endsWith("/")) {
      base = base + "/";
    }
    return base + code + "/";
  }

  /** 挂到新父级：父级为空表示提到根。 */
  public void moveTo(String parentCode, Integer parentLevel, String parentPath, Integer sort) {
    this.parentCode = parentCode;
    this.level = parentLevel == null ? 1 : parentLevel + 1;
    this.path = pathOf(parentPath, code);
    if (sort != null) {
      this.sort = sort;
    }
    this.updatedAt = LocalDateTime.now();
  }

  /** {@code ancestorCode} 是否在自身路径上——用于环检测（把自己挂到自己的子孙下）。 */
  public boolean hasAncestor(String ancestorCode) {
    return ancestorCode != null && path != null && path.contains("/" + ancestorCode + "/");
  }

  public boolean isRoot() {
    return parentCode == null || parentCode.isBlank();
  }

  public boolean isDefaultView() {
    return DEFAULT_HIERARCHY.equals(hierarchyCode);
  }

  /** 层级编码校验（与项编码同规则，长度更宽松以容纳视图名）。 */
  public static String normalizeHierarchyCode(String hierarchyCode) {
    if (hierarchyCode == null || hierarchyCode.isBlank()) {
      return DEFAULT_HIERARCHY;
    }
    String trimmed = hierarchyCode.trim();
    if (trimmed.length() > 64) {
      throw BizException.of("层级视图编码长度不能超过 64");
    }
    return trimmed;
  }
}
