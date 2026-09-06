package com.bone.integration.application.query.qry;

/** 流程统计查询对象：flowId 为 null 表示汇总全部流程 */
public record FlowStatisticsQuery(Long flowId) {}
