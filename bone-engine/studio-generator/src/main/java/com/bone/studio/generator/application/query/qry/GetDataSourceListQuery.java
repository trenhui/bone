package com.bone.studio.generator.application.query.qry;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GetDataSourceListQuery {
    private Integer page;
    private Integer size;
    private String name;
    private String type;
    private String status;
}
