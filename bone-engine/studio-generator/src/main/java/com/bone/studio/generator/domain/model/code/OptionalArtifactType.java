package com.bone.studio.generator.domain.model.code;

import java.util.ArrayList;
import java.util.List;

/**
 * 开关控制的附加产物类型（聚合纯单测 / 接口文档）。
 *
 * <p><b>为何放在 domain</b>：catalog 链路（infrastructure 的 {@code CodeGeneratorServiceImpl}）与物理库链路
 * （application 的 {@code CreateCodeGenerationApplicationService}）都要按开关决定产出什么，映射只有一份才不会漂移； 而
 * application 不能依赖 infrastructure，所以这份知识必须落到 domain。
 *
 * <p><b>为何不做成可勾选模板行</b>：物理库链路按用户勾选的 {@code templateIds} 产出，而模板管理里勾选的行 与「includeTests /
 * includeDocumentation」开关是两套入口——做成行就会出现「勾了模板但不产出」。
 */
public final class OptionalArtifactType {

  /** 聚合纯单测（{@code src/test/java/...}），对应 {@code includeTests}。 */
  public static final String AGGREGATE_TEST = "aggregateTest";

  /** 接口文档（{@code docs/{聚合}-api.md}），对应 {@code includeDocumentation}。 */
  public static final String API_DOC = "apiDoc";

  private OptionalArtifactType() {}

  public static List<String> all() {
    return List.of(AGGREGATE_TEST, API_DOC);
  }

  /** 按开关给出要额外产出的模板类型。 */
  public static List<String> typesFor(boolean includeTests, boolean includeDocumentation) {
    List<String> types = new ArrayList<>(2);
    if (includeTests) {
      types.add(AGGREGATE_TEST);
    }
    if (includeDocumentation) {
      types.add(API_DOC);
    }
    return types;
  }
}
