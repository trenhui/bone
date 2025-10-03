package com.bone.metadata.sdk.test.domain;

import com.bone.core.annotation.Table;
import com.bone.core.domain.entity.Entity;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    private LocalDateTime createTime;

    private String region;

    private String productName;

    private Integer quantity;

    private Boolean isDeleted;
}