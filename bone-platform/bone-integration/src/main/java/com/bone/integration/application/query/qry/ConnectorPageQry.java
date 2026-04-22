package com.bone.integration.application.query.qry;

public record ConnectorPageQry(int pageNum, int pageSize, String keyword, String type, String status) {
}