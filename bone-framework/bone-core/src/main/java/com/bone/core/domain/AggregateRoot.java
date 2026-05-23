package com.bone.core.domain;

import com.bone.core.annotation.Transient;
import com.bone.core.domain.entity.Entity;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 聚合根基类（领域事件）。审计字段见 {@link com.bone.core.domain.entity.AbstractEntity} /
 * {@link com.bone.core.tenant.TenantAbstractEntity}；需审计的聚合可继承 {@link AuditableAggregateRoot}（ADR-0011 阶段 2）。
 */
@Getter
public abstract class AggregateRoot<ID> extends Entity<ID> {

    @Transient
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected void addDomainEvent(DomainEvent event) {
        if (event != null) {
            domainEvents.add(event);
        }
    }

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }

    /** 供 SDK 回填主键 */
    public void setId(ID id) {
        super.setId(id);
    }
}
