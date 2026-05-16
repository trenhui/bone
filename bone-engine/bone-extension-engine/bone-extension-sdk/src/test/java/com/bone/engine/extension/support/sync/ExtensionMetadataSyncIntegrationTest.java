package com.bone.engine.extension.support.sync;

import com.bone.engine.extension.api.model.definition.ExtensionDefinition;
import com.bone.engine.extension.api.model.sync.ExtensionRoutingMetadata;
import com.bone.engine.extension.core.router.DefaultExtensionPointRouter;
import com.bone.engine.extension.support.context.BizContext;
import com.bone.engine.extension.support.expression.SpELExpressionEvaluator;
import com.bone.engine.extension.support.repository.InMemoryExtensionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Studio 发布元数据 → 叠加仓库 → 路由器 端到端（内存实现，等价于 Redis 路径）。
 */
class ExtensionMetadataSyncIntegrationTest {

    private static final String EXT_POINT = SampleExtPoint.class.getName();

    private InMemoryExtensionRepository local;
    private InMemoryExtensionMetadataStore metadataStore;
    private MetadataOverlayExtensionRepository overlayRepo;
    private DefaultExtensionPointRouter router;

    private final SampleExtPoint highPriority = () -> "HIGH";
    private final SampleExtPoint lowPriority = () -> "LOW";

    @BeforeEach
    void setUp() {
        local = new InMemoryExtensionRepository();
        metadataStore = new InMemoryExtensionMetadataStore();
        overlayRepo =
                new MetadataOverlayExtensionRepository(local, metadataStore, new SpELExpressionEvaluator());
        router =
                new DefaultExtensionPointRouter(
                        overlayRepo, new SpELExpressionEvaluator(), 1000, Duration.ofMinutes(5), true, null);

        local.registerExtension(
                EXT_POINT,
                ExtensionDefinition.builder()
                        .code("HIGH")
                        .extensionPoint(EXT_POINT)
                        .instance(highPriority)
                        .tenant("default")
                        .bizCode("BIZ")
                        .priority(10)
                        .build());
        local.registerExtension(
                EXT_POINT,
                ExtensionDefinition.builder()
                        .code("LOW")
                        .extensionPoint(EXT_POINT)
                        .instance(lowPriority)
                        .tenant("default")
                        .bizCode("BIZ")
                        .priority(200)
                        .build());
    }

    @Test
    void publishMetadata_disablesHighPriorityExtension() {
        metadataStore.save(ExtensionRoutingMetadata.builder()
                .extensionPoint(EXT_POINT)
                .code("HIGH")
                .enabled(false)
                .priority(10)
                .build());
        metadataStore.publishRefresh(EXT_POINT);
        router.clearCache(SampleExtPoint.class);

        BizContext<String> ctx =
                BizContext.<String>builder().tenant("default").bizCode("BIZ").data("x").build();

        assertSame(lowPriority, router.route(SampleExtPoint.class, ctx));
    }

    @Test
    void overlayDoesNotMutateLocalRepositoryDefinition() {
        metadataStore.save(ExtensionRoutingMetadata.builder()
                .extensionPoint(EXT_POINT)
                .code("HIGH")
                .enabled(false)
                .build());

        ExtensionDefinition localDef =
                local.getExtensionByCode(EXT_POINT, "HIGH").orElseThrow();
        assertTrue(localDef.isEnabled());
        assertFalse(overlayRepo.getEnabledExtensions(EXT_POINT).stream()
                .anyMatch(d -> "HIGH".equals(d.getCode())));
    }

    @Test
    void overlayAppliesConditionAndTrafficFromConfig() {
        metadataStore.save(ExtensionRoutingMetadata.builder()
                .extensionPoint(EXT_POINT)
                .code("LOW")
                .condition("#data == 'VIP'")
                .traffic(100)
                .priority(1)
                .enabled(true)
                .build());
        router.clearCache(SampleExtPoint.class);

        BizContext<String> vip =
                BizContext.<String>builder().tenant("default").bizCode("BIZ").data("VIP").requestId("r1").build();
        assertSame(lowPriority, router.route(SampleExtPoint.class, vip));
    }

    @FunctionalInterface
    interface SampleExtPoint {
        String tag();
    }
}
