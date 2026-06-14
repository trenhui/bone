package com.bone.engine.extension.support.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.engine.extension.api.model.definition.ExtensionDefinition;
import com.bone.engine.extension.api.model.sync.ExtensionRoutingMetadata;
import com.bone.engine.extension.support.expression.SpELExpressionEvaluator;
import com.bone.engine.extension.support.repository.InMemoryExtensionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetadataOverlayExtensionRepositoryTest {

  private static final String EXT_POINT = "com.example.ExtPoint";

  private InMemoryExtensionRepository local;
  private InMemoryExtensionMetadataStore metadataStore;
  private MetadataOverlayExtensionRepository repository;

  @BeforeEach
  void setUp() {
    local = new InMemoryExtensionRepository();
    metadataStore = new InMemoryExtensionMetadataStore();
    repository =
        new MetadataOverlayExtensionRepository(local, metadataStore, new SpELExpressionEvaluator());
    local.registerExtension(
        EXT_POINT,
        ExtensionDefinition.builder()
            .code("IMPL_A")
            .extensionPoint(EXT_POINT)
            .instance("bean-a")
            .tenant("default")
            .enabled(true)
            .build());
  }

  @Test
  void overlayCanDisableExtensionForRouting() {
    metadataStore.save(
        ExtensionRoutingMetadata.builder()
            .extensionPoint(EXT_POINT)
            .code("IMPL_A")
            .enabled(false)
            .build());

    assertTrue(local.getEnabledExtensions(EXT_POINT).stream().findAny().isPresent());
    assertFalse(repository.getEnabledExtensions(EXT_POINT).stream().findAny().isPresent());
  }

  @Test
  void overlayDoesNotMutateUnderlyingDefinition() {
    metadataStore.save(
        ExtensionRoutingMetadata.builder()
            .extensionPoint(EXT_POINT)
            .code("IMPL_A")
            .priority(1)
            .traffic(10)
            .enabled(false)
            .build());

    ExtensionDefinition underlying = local.getExtensionByCode(EXT_POINT, "IMPL_A").orElseThrow();
    assertTrue(underlying.isEnabled());
    assertEquals(100, underlying.getPriority());

    ExtensionDefinition overlaid = repository.getExtensionByCode(EXT_POINT, "IMPL_A").orElseThrow();
    assertFalse(overlaid.isEnabled());
    assertEquals(1, overlaid.getPriority());
  }
}
