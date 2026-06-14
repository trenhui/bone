package com.bone.integration.application.query.qry;

public record ConnectorPageQuery(
    int pageNum, int pageSize, String keyword, String type, String status) {}
