package com.bone.iam.domain.model.menu.event;

import com.bone.core.domain.DomainEvent;
import com.bone.iam.domain.model.menu.Menu;
import lombok.Getter;

@Getter
public class MenuCreatedEvent implements DomainEvent {
  private final Menu menu;

  public MenuCreatedEvent(Menu menu) {
    this.menu = menu;
  }
}
