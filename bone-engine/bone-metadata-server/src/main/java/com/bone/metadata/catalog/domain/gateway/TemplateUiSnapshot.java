package com.bone.metadata.catalog.domain.gateway;

/**
 * 平台模板界面快照读模型（迁移 0046：模板资产 + 版本历史）。
 *
 * <p>纯读模型（record），供模板跟随三路归并取 {@code base.new} / {@code base.old}。
 *
 * @param version 模板界面版本（base_version 来源）
 * @param schemaJson 该版本的 baseUiSchema 全量（可能为空 = 资产未补齐）
 */
public record TemplateUiSnapshot(String version, String schemaJson) {

  public boolean hasSchema() {
    return schemaJson != null && !schemaJson.isBlank();
  }
}
