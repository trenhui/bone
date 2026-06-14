package com.bone.system.domain.model.console;

import lombok.Builder;
import lombok.Value;

/** 控制台读侧值对象：快捷入口（与 bone-shell 微应用路由对齐）。 */
@Value
@Builder
public class QuickAction {

  String id;
  String title;
  String path;
  String icon;
}
