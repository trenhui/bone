/**
 * domain 层写侧仓储端口：聚合根的持久化接口。
 *
 * <p>所有仓储继承 {@code bone-metadata-sdk} 的 {@link com.bone.core.repository.Repository}， 由
 * {@code @EnableSqlRepositories} 注解在启动类上代理实现，不需要手动写仓储实现类。 这是平台唯一的持久化方案，禁止引入 MyBatis / JPA /
 * Hibernate / MyBatis-Plus 等其他 ORM。
 *
 * <p>仓储端口只表达业务语义（save / findByIdInTenant），不承载 SQL / QueryBuilder。 读侧复杂查询（分页、JOIN、聚合）走
 * application/query/port。
 */
package com.bone.blueprint.domain.repository;
