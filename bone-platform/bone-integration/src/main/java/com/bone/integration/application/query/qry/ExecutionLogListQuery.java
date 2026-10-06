package com.bone.integration.application.query.qry;

public record ExecutionLogListQuery(int page, int size, Long flowId, String status) {}
