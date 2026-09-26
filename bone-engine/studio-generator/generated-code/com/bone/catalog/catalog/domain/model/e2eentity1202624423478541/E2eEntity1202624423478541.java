package com.bone.catalog.catalog.domain.model.e2eentity1202624423478541;

import com.bone.core.annotation.Id;
import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * E2E实体 (物理表: meta_e2e_1202624423590500)。
 *
 * <p>由代码生成器基于表 e2e_entity_1202624423478541 生成。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("e2e_entity_1202624423478541")
public class E2eEntity1202624423478541 extends AggregateRoot<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
