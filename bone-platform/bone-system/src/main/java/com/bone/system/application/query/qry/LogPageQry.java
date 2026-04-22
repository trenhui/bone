package com.bone.system.application.query.qry;

import lombok.Data;

@Data
public class LogPageQry {
    private String keyword;
    private String logLevel;
    private String serviceName;
    private int pageNum = 1;
    private int pageSize = 10;
}
