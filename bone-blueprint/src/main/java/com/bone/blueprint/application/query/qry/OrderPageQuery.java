
package com.bone.blueprint.application.query.qry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPageQuery {

    private String customerId;
    private String status;
    private Integer pageNo;
    private Integer pageSize;
}

