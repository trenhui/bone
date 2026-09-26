package com.bone.masterdata.domain.model.category;

import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 主数据分类（G4，UC-T3）：实体内树形分类体系，支持父子层级。 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("mdm_category")
public class MasterDataCategory extends TenantAggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  /** 所属主数据实体ID；列名 mdm_entity_id。 */
  @Column(name = "mdm_entity_id")
  private Long masterDataEntityId;

  private String name;

  /** 实体内唯一分类编码。 */
  private String code;

  private String description;

  /** 父分类ID；空表示根节点。 */
  @Column(name = "parent_category_id")
  private Long parentCategoryId;

  /** 层级（根为 0）。 */
  private Integer level;

  @Column(name = "sort_order")
  private Integer sortOrder;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static MasterDataCategory create(
      Long id,
      Long masterDataEntityId,
      String code,
      String name,
      String description,
      Long parentCategoryId,
      Integer parentLevel,
      Integer sortOrder) {
    MasterDataCategory category = new MasterDataCategory();
    category.id = id;
    category.masterDataEntityId = masterDataEntityId;
    category.code = code;
    category.name = name;
    category.description = description;
    category.parentCategoryId = parentCategoryId;
    category.level = parentCategoryId == null ? 0 : parentLevel + 1;
    category.sortOrder = sortOrder == null ? 0 : sortOrder;
    category.createdAt = LocalDateTime.now();
    category.updatedAt = LocalDateTime.now();
    return category;
  }

  public void update(String name, String description, Integer sortOrder) {
    this.name = name;
    this.description = description;
    if (sortOrder != null) {
      this.sortOrder = sortOrder;
    }
    this.updatedAt = LocalDateTime.now();
  }

  /** 父子约束：分类不能挂到自身或自身子孙（由应用层传入校验结果表达，此处防御父=自身）。 */
  public void ensureNotSelfParent(Long requestedParentId) {
    if (requestedParentId != null && requestedParentId.equals(this.id)) {
      throw new DomainException("分类不能挂到自己下面");
    }
  }
}
