package com.bone.system.application.query.qry;

import lombok.Data;

@Data
public class ConfigPageQuery {
    private String keyword;
    private String configType;
    private int pageNum = 1;
    private int pageSize = 10;
}
