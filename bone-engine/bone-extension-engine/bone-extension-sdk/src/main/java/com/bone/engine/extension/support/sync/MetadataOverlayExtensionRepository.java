package com.bone.engine.extension.support.sync;

import com.bone.engine.extension.api.model.definition.ExtensionDefinition;
import com.bone.engine.extension.api.model.sync.ExtensionRoutingMetadata;
import com.bone.engine.extension.api.spi.ExpressionEvaluator;
import com.bone.engine.extension.api.spi.ExtensionRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/** 在本地注册的定义之上叠加控制面元数据（实例仍来自 Spring 容器；读路径 copy-on-read）。 */
@Slf4j
public class MetadataOverlayExtensionRepository implements ExtensionRepository {

  private final ExtensionRepository delegate;
  private final ExtensionMetadataStore metadataStore;
  private final ExpressionEvaluator expressionEvaluator;

  public MetadataOverlayExtensionRepository(
      ExtensionRepository delegate,
      ExtensionMetadataStore metadataStore,
      ExpressionEvaluator expressionEvaluator) {
    this.delegate = delegate;
    this.metadataStore = metadataStore;
    this.expressionEvaluator = expressionEvaluator;
  }

  @Override
  @Nullable
  public ExtensionDefinition registerExtension(
      @NonNull String extensionPoint, @NonNull ExtensionDefinition extension) {
    return delegate.registerExtension(extensionPoint, extension);
  }

  @Override
  @Nullable
  public ExtensionDefinition unregisterExtension(
      @NonNull String extensionPoint, @NonNull String extensionCode) {
    metadataStore.remove(extensionPoint, extensionCode);
    return delegate.unregisterExtension(extensionPoint, extensionCode);
  }

  @Override
  @NonNull
  public Collection<ExtensionDefinition> getEnabledExtensions(@NonNull String extensionPoint) {
    return overlayAll(extensionPoint).stream()
        .filter(ExtensionDefinition::isEnabled)
        .collect(Collectors.toUnmodifiableList());
  }

  @Override
  @NonNull
  public Collection<ExtensionDefinition> getAllExtensions(@NonNull String extensionPoint) {
    return overlayAll(extensionPoint);
  }

  @Override
  @NonNull
  public Optional<ExtensionDefinition> getExtensionByCode(
      @NonNull String extensionPoint, @NonNull String extensionCode) {
    return delegate
        .getExtensionByCode(extensionPoint, extensionCode)
        .map(def -> overlayOne(def, metadataStore.get(extensionPoint, extensionCode)));
  }

  @Override
  public int batchRegisterExtensions(
      @NonNull Map<String, Collection<ExtensionDefinition>> extensionsByPoint) {
    return delegate.batchRegisterExtensions(extensionsByPoint);
  }

  @Override
  public int clearExtensionPoint(@NonNull String extensionPoint) {
    metadataStore.clearExtensionPoint(extensionPoint);
    return delegate.clearExtensionPoint(extensionPoint);
  }

  @Override
  public void clearAllExtensions() {
    delegate.clearAllExtensions();
  }

  @Override
  @NonNull
  public java.util.Set<String> getAllExtensionPointNames() {
    return delegate.getAllExtensionPointNames();
  }

  @Override
  public boolean hasExtensions(@NonNull String extensionPoint) {
    return delegate.hasExtensions(extensionPoint);
  }

  @Override
  @NonNull
  public ExtensionRepositoryStats getRepositoryStats() {
    return delegate.getRepositoryStats();
  }

  @NonNull
  private Collection<ExtensionDefinition> overlayAll(@NonNull String extensionPoint) {
    Map<String, ExtensionRoutingMetadata> overlay =
        metadataStore.getByExtensionPoint(extensionPoint);
    return delegate.getAllExtensions(extensionPoint).stream()
        .map(def -> overlayOne(def, overlay.get(def.getCode())))
        .collect(Collectors.toUnmodifiableList());
  }

  @NonNull
  private ExtensionDefinition overlayOne(
      ExtensionDefinition source, @Nullable ExtensionRoutingMetadata meta) {
    ExtensionDefinition view = source.copyRoutingView();
    if (meta == null) {
      return view;
    }
    view.applyRoutingOverlay(meta);
    if (org.springframework.util.StringUtils.hasText(view.getCondition())
        && view.getConditionPredicate() == null) {
      try {
        view.setConditionPredicate(expressionEvaluator.compile(view.getCondition()));
      } catch (Exception e) {
        log.warn(
            "Failed to compile overlay condition for {}: {}",
            view.getCode(),
            view.getCondition(),
            e);
      }
    }
    return view;
  }
}
