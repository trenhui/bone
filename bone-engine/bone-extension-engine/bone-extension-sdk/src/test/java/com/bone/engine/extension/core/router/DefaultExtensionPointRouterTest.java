package com.bone.engine.extension.core.router;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.engine.extension.api.model.definition.ExtensionDefinition;
import com.bone.engine.extension.support.context.BizContext;
import com.bone.engine.extension.support.expression.SpELExpressionEvaluator;
import com.bone.engine.extension.support.repository.InMemoryExtensionRepository;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultExtensionPointRouterTest {

  private static final String EXT_POINT = SampleExtPoint.class.getName();

  private InMemoryExtensionRepository repository;
  private DefaultExtensionPointRouter router;

  private final SampleExtPoint outpatientImpl = () -> "OUTPATIENT";
  private final SampleExtPoint inpatientImpl = () -> "INPATIENT";
  private final SampleExtPoint broadImpl = () -> "BROAD";

  @BeforeEach
  void setUp() {
    repository = new InMemoryExtensionRepository();
    router =
        new DefaultExtensionPointRouter(
            repository, new SpELExpressionEvaluator(), 1000, Duration.ofMinutes(10), true, null);

    repository.registerExtension(
        EXT_POINT,
        ExtensionDefinition.builder()
            .code("OUTPATIENT")
            .extensionPoint(EXT_POINT)
            .instance(outpatientImpl)
            .tenant("default")
            .bizCode("MEDICAL")
            .condition("#data.type == 'OUTPATIENT'")
            .priority(100)
            .build());

    repository.registerExtension(
        EXT_POINT,
        ExtensionDefinition.builder()
            .code("INPATIENT")
            .extensionPoint(EXT_POINT)
            .instance(inpatientImpl)
            .tenant("default")
            .bizCode("MEDICAL")
            .scenario("INPATIENT")
            .condition("#data.type == 'INPATIENT'")
            .priority(110)
            .build());

    repository.registerExtension(
        EXT_POINT,
        ExtensionDefinition.builder()
            .code("BROAD")
            .extensionPoint(EXT_POINT)
            .instance(broadImpl)
            .tenant("default")
            .bizCode("MEDICAL")
            .priority(200)
            .build());
  }

  @Test
  void routesInpatientBySpelNotBroadDimensionMatch() {
    BizContext<ClaimPayload> ctx =
        BizContext.<ClaimPayload>builder()
            .tenant("default")
            .bizCode("MEDICAL")
            .data(new ClaimPayload("INPATIENT"))
            .build();

    Object routed = router.route(SampleExtPoint.class, ctx);
    assertSame(inpatientImpl, routed);
  }

  @Test
  void routesOutpatientBySpel() {
    BizContext<ClaimPayload> ctx =
        BizContext.<ClaimPayload>builder()
            .tenant("default")
            .bizCode("MEDICAL")
            .data(new ClaimPayload("OUTPATIENT"))
            .build();

    Object routed = router.route(SampleExtPoint.class, ctx);
    assertSame(outpatientImpl, routed);
  }

  @Test
  void routeResultCacheRespectsDifferentPayloadFingerprint() {
    DefaultExtensionPointRouter cachingRouter =
        new DefaultExtensionPointRouter(
            repository,
            new SpELExpressionEvaluator(),
            1000,
            Duration.ofMinutes(10),
            true,
            new com.bone.engine.extension.core.cache.CacheManager(cacheProperties(), null));

    BizContext<ClaimPayload> inpatient =
        BizContext.<ClaimPayload>builder()
            .tenant("default")
            .bizCode("MEDICAL")
            .requestId("req-1")
            .data(new ClaimPayload("INPATIENT"))
            .build();
    BizContext<ClaimPayload> outpatient =
        BizContext.<ClaimPayload>builder()
            .tenant("default")
            .bizCode("MEDICAL")
            .requestId("req-2")
            .data(new ClaimPayload("OUTPATIENT"))
            .build();

    assertSame(inpatientImpl, cachingRouter.route(SampleExtPoint.class, inpatient));
    assertSame(outpatientImpl, cachingRouter.route(SampleExtPoint.class, outpatient));
  }

  @Test
  void nonStrictModeFallsBackToBroadestExtension() {
    DefaultExtensionPointRouter lenientRouter =
        new DefaultExtensionPointRouter(
            repository,
            new SpELExpressionEvaluator(),
            1000,
            Duration.ofMinutes(10),
            true,
            null,
            false,
            false);

    BizContext<ClaimPayload> ctx =
        BizContext.<ClaimPayload>builder()
            .tenant("default")
            .bizCode("MEDICAL")
            .data(new ClaimPayload("UNKNOWN"))
            .build();

    assertSame(broadImpl, lenientRouter.route(SampleExtPoint.class, ctx));
  }

  @Test
  void firstStrategyUsesSortedListOrderNotExpressionLayer() {
    InMemoryExtensionRepository firstRepo = new InMemoryExtensionRepository();
    SampleExtPoint dimFirst = () -> "DIM_FIRST";
    SampleExtPoint exprSecond = () -> "EXPR_SECOND";

    firstRepo.registerExtension(
        EXT_POINT,
        ExtensionDefinition.builder()
            .code("DIM_FIRST")
            .extensionPoint(EXT_POINT)
            .instance(dimFirst)
            .tenant("default")
            .bizCode("MEDICAL")
            .scenario("SPECIAL")
            .priority(50)
            .build());
    firstRepo.registerExtension(
        EXT_POINT,
        ExtensionDefinition.builder()
            .code("EXPR_SECOND")
            .extensionPoint(EXT_POINT)
            .instance(exprSecond)
            .tenant("*")
            .bizCode("*")
            .condition("#data.type == 'SPECIAL'")
            .priority(5)
            .build());

    DefaultExtensionPointRouter firstRouter =
        new DefaultExtensionPointRouter(
            firstRepo,
            new SpELExpressionEvaluator(),
            1000,
            Duration.ofMinutes(10),
            true,
            null,
            true,
            false,
            "first");
    DefaultExtensionPointRouter scoreRouter =
        new DefaultExtensionPointRouter(
            firstRepo,
            new SpELExpressionEvaluator(),
            1000,
            Duration.ofMinutes(10),
            true,
            null,
            true,
            false,
            "score");

    BizContext<ClaimPayload> ctx =
        BizContext.<ClaimPayload>builder()
            .tenant("default")
            .bizCode("MEDICAL")
            .scenario("SPECIAL")
            .data(new ClaimPayload("SPECIAL"))
            .build();

    assertSame(dimFirst, firstRouter.route(SampleExtPoint.class, ctx));
    assertSame(exprSecond, scoreRouter.route(SampleExtPoint.class, ctx));
  }

  @Test
  void strictModeThrowsWhenNoMatch() {
    InMemoryExtensionRepository strictRepo = new InMemoryExtensionRepository();
    strictRepo.registerExtension(
        EXT_POINT,
        ExtensionDefinition.builder()
            .code("INPATIENT")
            .extensionPoint(EXT_POINT)
            .instance(inpatientImpl)
            .tenant("default")
            .bizCode("MEDICAL")
            .condition("#data.type == 'INPATIENT'")
            .build());

    DefaultExtensionPointRouter strictRouter =
        new DefaultExtensionPointRouter(
            strictRepo,
            new SpELExpressionEvaluator(),
            1000,
            Duration.ofMinutes(10),
            true,
            null,
            true,
            false);

    BizContext<ClaimPayload> ctx =
        BizContext.<ClaimPayload>builder()
            .tenant("default")
            .bizCode("MEDICAL")
            .data(new ClaimPayload("UNKNOWN"))
            .build();

    assertThrows(
        DefaultExtensionPointRouter.RouterException.class,
        () -> strictRouter.route(SampleExtPoint.class, ctx));
  }

  private static com.bone.engine.extension.support.config.ExtensionProperties cacheProperties() {
    com.bone.engine.extension.support.config.ExtensionProperties properties =
        new com.bone.engine.extension.support.config.ExtensionProperties();
    properties.getCache().setEnabled(true);
    properties.getCache().setMaxSize(500);
    properties.getCache().setExpireAfterWrite(Duration.ofMinutes(5).toMillis());
    properties.getRouter().setCacheEnabled(true);
    return properties;
  }

  @FunctionalInterface
  interface SampleExtPoint {
    String tag();
  }

  static final class ClaimPayload {
    private final String type;

    ClaimPayload(String type) {
      this.type = type;
    }

    public String getType() {
      return type;
    }
  }
}
