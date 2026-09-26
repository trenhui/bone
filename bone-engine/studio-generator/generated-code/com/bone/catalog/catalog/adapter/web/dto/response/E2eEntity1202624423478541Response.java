package com.bone.catalog.catalog.adapter.web.dto.response;

import com.bone.catalog.catalog.domain.model.e2eentity1202624423478541.E2eEntity1202624423478541;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * E2E实体 (物理表: meta_e2e_1202624423590500)响应对象。
 *
 * <p>由代码生成器基于表 e2e_entity_1202624423478541 生成。HC-003：Controller 不裸返领域对象。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class E2eEntity1202624423478541Response {

  private Long id;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static E2eEntity1202624423478541Response from(E2eEntity1202624423478541 entity) {
    return E2eEntity1202624423478541Response.builder()
        .id(entity.getId())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
