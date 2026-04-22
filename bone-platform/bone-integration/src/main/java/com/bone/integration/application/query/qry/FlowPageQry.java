package com.bone.integration.application.query.qry;

public record FlowPageQry(int pageNum, int pageSize, String keyword, String status) {
}