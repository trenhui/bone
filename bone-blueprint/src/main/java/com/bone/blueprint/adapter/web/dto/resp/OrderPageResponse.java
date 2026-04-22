
package com.bone.blueprint.adapter.web.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPageResponse {

    private String id;
    private String customerId;
    private BigDecimal totalAmount;
    private String status;
    private String statusDescription;
    private String recipientName;
    private String phone;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

