package com.bone.iam.domain.menu.event;

import com.bone.core.domain.DomainEvent;
import com.bone.iam.domain.menu.Menu;
import lombok.Getter;

@Getter
public class MenuCreatedEvent implements DomainEvent {
  private final Menu menu;

  public MenuCreatedEvent(Menu menu) {
    this.menu = menu;
  }
}
