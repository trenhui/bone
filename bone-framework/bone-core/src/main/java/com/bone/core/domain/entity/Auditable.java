package com.bone.core.domain.entity;

import java.util.Date;

/**
 * 审计数据接口
 * @param <U>
 *
 */
public interface Auditable<U> {
    U getCreateBy();
    void setCreateBy(U createBy);

    Date getCreateTime();
    void setCreateTime(Date createTime);

    U getUpdateBy();
    void setUpdateBy(U updateBy);

    Date getUpdateTime();
    void setUpdateTime(Date updateTime);
}