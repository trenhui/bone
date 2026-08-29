package com.bone.blueprint.application.query.qry;

/**
 * 订单分页查询对象（不可变 record）。
 *
 * @param customerId 客户 ID（可空过滤）
 * @param status 订单状态（可空过滤）
 * @param pageSize 每页大小（可空，默认 10）
 * @param pageNum 页码（可空，默认 1）
 */
public record OrderPageQuery(Long customerId, String status, Integer pageSize, Integer pageNum) {}
