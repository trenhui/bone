package com.bone.integration.domain.model.connector.valueobject;

public enum ConnectorStatus {
  ENABLED,
  DISABLED,
  TESTING,
  ERROR;

  public boolean isEnabled() {
    return this == ENABLED;
  }

  public boolean isDisabled() {
    return this == DISABLED;
  }

  public boolean isTesting() {
    return this == TESTING;
  }

  public boolean isError() {
    return this == ERROR;
  }
}
