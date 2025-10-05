package com.bone.core.domain.entity;

/**
 * 租户接口
 * @param <T>
 */
public interface Tenantable<T> {
    T getTenantId();
    void setTenantId(T tenantId);
}