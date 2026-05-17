package com.bone.core.domain.entity;

import java.util.Date;

/**
 * 审计数据接口
 * @param <U>
 *
 */
public interface Auditable<U> {
    U getCreatedBy();
    void setCreatedBy(U createdBy);

    Date getCreatedAt();
    void setCreatedAt(Date createdAt);

    U getUpdatedBy();
    void setUpdatedBy(U updatedBy);

    Date getUpdatedAt();
    void setUpdatedAt(Date updatedAt);
}