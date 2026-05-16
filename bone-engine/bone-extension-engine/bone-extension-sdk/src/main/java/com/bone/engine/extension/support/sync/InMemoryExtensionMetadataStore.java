package com.bone.engine.extension.support.sync;

import com.bone.engine.extension.api.model.sync.ExtensionRoutingMetadata;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/** 内存元数据存储（单测 / 无 Redis 环境）。 */
public class InMemoryExtensionMetadataStore implements ExtensionMetadataStore {

  private final Map<String, ExtensionRoutingMetadata> storage = new ConcurrentHashMap<>();
  private final CopyOnWriteArrayList<Consumer<String>> refreshListeners =
      new CopyOnWriteArrayList<>();

  @Override
  public void save(@NonNull ExtensionRoutingMetadata metadata) {
    storage.put(
        ExtensionMetadataKeys.metadataKey(metadata.getExtensionPoint(), metadata.getCode()),
        metadata);
  }

  @Override
  public void remove(@NonNull String extensionPoint, @NonNull String code) {
    storage.remove(ExtensionMetadataKeys.metadataKey(extensionPoint, code));
  }

  @Override
  @Nullable
  public ExtensionRoutingMetadata get(@NonNull String extensionPoint, @NonNull String code) {
    return storage.get(ExtensionMetadataKeys.metadataKey(extensionPoint, code));
  }

  @Override
  @NonNull
  public Map<String, ExtensionRoutingMetadata> getByExtensionPoint(@NonNull String extensionPoint) {
    String prefix = ExtensionMetadataKeys.metadataKey(extensionPoint, "");
    return storage.entrySet().stream()
        .filter(e -> e.getKey().startsWith(prefix))
        .collect(Collectors.toMap(e -> e.getValue().getCode(), Map.Entry::getValue, (a, b) -> b));
  }

  @Override
  public void publishRefresh(@NonNull String extensionPoint) {
    refreshListeners.forEach(listener -> listener.accept(extensionPoint));
  }

  @Override
  public void clearExtensionPoint(@NonNull String extensionPoint) {
    String prefix = ExtensionMetadataKeys.metadataKey(extensionPoint, "");
    storage.keySet().removeIf(k -> k.startsWith(prefix));
  }

  public void addRefreshListener(Consumer<String> listener) {
    refreshListeners.add(listener);
  }
}
