package com.bone.integration.application.query.qry;

public record ExecutionLogListQuery(int pageNum, int pageSize, Long flowId, String status) {}
