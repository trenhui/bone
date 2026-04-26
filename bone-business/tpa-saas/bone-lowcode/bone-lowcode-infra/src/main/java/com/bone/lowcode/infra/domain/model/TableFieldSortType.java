package com.bone.lowcode.infra.domain.model;

import lombok.Data;

@Data
public class TableFieldSortType {

    /**
     * 字段id
     */
    private String id;

    /**
     * 字段code
     */
    private String field;

    /**
     * 排序方式，asc、desc
     */
    private String order;
}
