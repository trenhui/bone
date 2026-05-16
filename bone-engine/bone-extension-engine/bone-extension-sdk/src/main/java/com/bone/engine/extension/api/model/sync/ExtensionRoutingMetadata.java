package com.bone.engine.extension.api.model.sync;

import java.io.Serial;
import java.io.Serializable;
import lombok.Builder;
import lombok.Data;

/** 扩展路由元数据（控制面下发，不含 Spring Bean 实例）。 */
@Data
@Builder
public class ExtensionRoutingMetadata implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String extensionPoint;
  private String code;
  private String tenant;
  private String bizCode;
  private String useCase;
  private String scenario;
  private String env;
  private String userGroup;
  private String condition;
  private int priority = 100;
  private int weight = 100;
  private int traffic = 100;
  private boolean enabled = true;
  private boolean defaultImpl;
  private long version;
}
