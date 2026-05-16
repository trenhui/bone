package com.bone.metadata.engine.metadata;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 包定义模型类 */
@Getter
@Setter
public class PackageDefinition {

  // 包名称
  private String name;

  // 包标签
  private String label;

  // 包描述
  private String description;

  // 版本号
  private String version;

  // 依赖的包
  private List<String> dependencies = new ArrayList<>();

  // 包含的实体
  private List<String> entities = new ArrayList<>();

  // 包含的工作流
  private List<String> workflows = new ArrayList<>();

  // 是否为系统包
  private boolean isSystemPackage = false;

  /** 获取包名称（用于兼容性） */
  public String getPackageName() {
    return this.name;
  }
}
