package com.bone.metadata.sdk.test.domain;

import com.bone.core.domain.entity.Entity;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@Table("sales_record")
@Builder
public class SalesRecord extends Entity<Long> {

  private String category;

  private BigDecimal amount;

  private BigDecimal price;

  private String status;

  private LocalDateTime createdAt;

  private String region;

  private String productName;

  private Integer quantity;

  private Boolean isDeleted;
}
