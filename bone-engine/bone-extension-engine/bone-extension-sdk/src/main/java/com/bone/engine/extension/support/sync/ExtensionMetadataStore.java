package com.bone.engine.extension.support.sync;

import com.bone.engine.extension.api.model.sync.ExtensionRoutingMetadata;
import java.util.Map;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/** 扩展路由元数据存储（控制面 → 数据面）。 */
public interface ExtensionMetadataStore {

  void save(@NonNull ExtensionRoutingMetadata metadata);

  void remove(@NonNull String extensionPoint, @NonNull String code);

  @Nullable
  ExtensionRoutingMetadata get(@NonNull String extensionPoint, @NonNull String code);

  @NonNull
  Map<String, ExtensionRoutingMetadata> getByExtensionPoint(@NonNull String extensionPoint);

  /** 通知运行时刷新（Redis Pub/Sub 或本地监听器） */
  void publishRefresh(@NonNull String extensionPoint);

  void clearExtensionPoint(@NonNull String extensionPoint);
}
