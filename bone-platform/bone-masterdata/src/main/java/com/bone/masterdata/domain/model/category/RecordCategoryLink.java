package com.bone.masterdata.domain.model.category;

import com.bone.core.domain.entity.Entity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 记录-分类关联（G4）：一条记录可属多分类（N:M）。 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("mdm_record_category")
public class RecordCategoryLink extends Entity<Long> {

  @Column(name = "record_id")
  private Long recordId;

  @Column(name = "category_id")
  private Long categoryId;

  private LocalDateTime createdAt;

  public static RecordCategoryLink create(Long id, Long recordId, Long categoryId) {
    RecordCategoryLink link = new RecordCategoryLink();
    link.setId(id);
    link.recordId = recordId;
    link.categoryId = categoryId;
    link.createdAt = LocalDateTime.now();
    return link;
  }
}
