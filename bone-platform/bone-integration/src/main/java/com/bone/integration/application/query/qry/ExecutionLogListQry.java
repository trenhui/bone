package com.bone.integration.application.query.qry;

public record ExecutionLogListQry(int pageNum, int pageSize, Long flowId, String status) {
}