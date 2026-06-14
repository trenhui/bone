package com.bone.core.domain;

import com.bone.core.annotation.Transient;
import com.bone.core.domain.entity.AbstractEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * 带框架审计字段（{@link java.util.Date}）的聚合根。模块若域内使用 {@link java.time.LocalDateTime} 审计，请暂用 {@link
 * AggregateRoot} 并自行声明审计字段（如 bone-iam），待统一后再迁移本基类。
 */
@Getter
public abstract class AuditableAggregateRoot<ID> extends AbstractEntity<ID> {

  @Transient private final List<DomainEvent> domainEvents = new ArrayList<>();

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

  public void setId(ID id) {
    super.setId(id);
  }
}
