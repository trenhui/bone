package com.bone.system.domain.dict;

import com.bone.core.annotation.Id;
import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.system.domain.dict.vo.DictType;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 系统字典聚合：类型分组 + 键值对。 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("sys_dict")
public class SysDict extends AggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private DictType type;
  private String typeName;
  private String code;
  private String label;
  private String value;
  private Integer sort;
  private Integer status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static SysDict create(
      Long id,
      DictType type,
      String typeName,
      String code,
      String label,
      String value,
      Integer sort,
      Integer status) {
    SysDict dict = new SysDict();
    dict.id = id;
    dict.type = type;
    dict.typeName = typeName;
    dict.code = code;
    dict.label = label;
    dict.value = value;
    dict.sort = sort == null ? 0 : sort;
    dict.status = status == null ? 1 : status;
    dict.createdAt = LocalDateTime.now();
    dict.updatedAt = LocalDateTime.now();
    return dict;
  }

  public void update(String typeName, String label, String value, Integer sort, Integer status) {
    this.typeName = typeName;
    this.label = label;
    this.value = value;
    this.sort = sort;
    this.status = status;
    this.updatedAt = LocalDateTime.now();
  }
}
