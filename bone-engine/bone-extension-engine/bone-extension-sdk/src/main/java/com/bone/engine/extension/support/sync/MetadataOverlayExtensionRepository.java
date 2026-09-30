package com.bone.engine.extension.support.sync;

import com.bone.engine.extension.api.model.definition.ExtensionDefinition;
import com.bone.engine.extension.api.model.sync.ExtensionRoutingMetadata;
import com.bone.engine.extension.api.spi.ExpressionEvaluator;
import com.bone.engine.extension.api.spi.ExtensionRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    Collection<ExtensionRoutingMetadata> overlay = listOverlay(extensionPoint);
    return delegate
        .getExtensionByCode(extensionPoint, extensionCode)
        .map(def -> overlayOne(def, findOverlay(overlay, def)));
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
    Collection<ExtensionRoutingMetadata> overlay = listOverlay(extensionPoint);
    return delegate.getAllExtensions(extensionPoint).stream()
        .map(def -> overlayOne(def, findOverlay(overlay, def)))
        .collect(Collectors.toUnmodifiableList());
  }

  /**
   * 控制面元数据与本地定义的匹配：code 精确优先，scenario+bizCode+useCase 语义匹配兜底。
   *
   * <p>code 是控制面插件名（studio 侧命名）与本地 {@code @Extension(name)} 的推导值——两者 分属不同命名空间，没有任何对齐保证；若仅按 code
   * 匹配，控制面元数据对本地定义恒为 miss（实测缺陷）。scenario/bizCode/useCase 是双方显式声明的语义路由键，以它兜底。
   */
  @NonNull
  private Collection<ExtensionRoutingMetadata> listOverlay(@NonNull String extensionPoint) {
    Map<String, ExtensionRoutingMetadata> byCode =
        metadataStore.getByExtensionPoint(extensionPoint);
    return byCode == null || byCode.isEmpty() ? List.of() : byCode.values();
  }

  @Nullable
  private ExtensionRoutingMetadata findOverlay(
      @NonNull Collection<ExtensionRoutingMetadata> overlay, @NonNull ExtensionDefinition def) {
    for (ExtensionRoutingMetadata meta : overlay) {
      if (def.getCode() != null && def.getCode().equals(meta.getCode())) {
        return meta;
      }
    }
    for (ExtensionRoutingMetadata meta : overlay) {
      if (meta.getScenario() != null
          && meta.getScenario().equals(def.getScenario())
          && Objects.equals(meta.getBizCode(), def.getBizCode())
          && Objects.equals(meta.getUseCase(), def.getUseCase())) {
        return meta;
      }
    }
    return null;
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
