package com.bone.metadata.engine.metadata;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 记录类型元数据模型类 */
@Getter
@Setter
public class RecordTypeMetadata {

  // 记录类型API名称
  private String apiName;

  // 记录类型标签
  private String label;

  // 记录类型描述
  private String description;

  // 是否为默认记录类型
  private boolean isDefault = false;

  // 可用的业务流程
  private List<String> availableWorkflows = new ArrayList<>();

  // 可见的配置文件
  private List<String> visibleProfiles = new ArrayList<>();
}
