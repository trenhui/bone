package com.bone.core.domain;

import com.bone.core.annotation.Transient;
import com.bone.core.domain.entity.Entity;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 聚合根基类
 *
 * @param <ID> 主键ID类型
 */
@Getter
public abstract class AggregateRoot<ID> extends Entity<ID> {

    /**
     * 领域事件列表
     */
    @Transient
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    /**
     * 添加领域事件
     *
     * @param event 领域事件
     */
    protected void addDomainEvent(DomainEvent event) {
        if (event != null) {
            domainEvents.add(event);
        }
    }

    /**
     * 获取所有领域事件（只读）
     *
     * @return 领域事件列表
     */
    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /**
     * 清空领域事件
     */
    public void clearDomainEvents() {
        domainEvents.clear();
    }

    /**
     * 设置ID（供SDK回填使用）
     *
     * @param id ID值
     */
    public void setId(ID id) {
        super.setId(id);
    }
}
