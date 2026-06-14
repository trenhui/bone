package com.bone.metadata.sdk.extension.handler;

import com.bone.metadata.sdk.domain.enums.ExtensionMode;
import com.bone.metadata.sdk.extension.ExtensionContext;
import java.util.Map;

/** 扩展字段存储处理器接口。 */
public interface ExtensionStorageHandler {
  ExtensionMode getMode();

  void save(ExtensionContext context);

  Map<String, Object> load(ExtensionContext context);
}
