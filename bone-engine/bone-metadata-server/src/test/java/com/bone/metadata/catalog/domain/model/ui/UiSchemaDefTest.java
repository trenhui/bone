package com.bone.metadata.catalog.domain.model.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 界面 schema 聚合不变量单测（迁移 0046）。 */
class UiSchemaDefTest {

  @Test
  @DisplayName("创建草稿：版本锁 1、锁 0、pinned 跟随")
  void create_shouldInitVersionLockAndFollow() {
    UiSchemaDef def = UiSchemaDef.create(7L, 9001L, "{\"schemaVersion\":1}", 1);

    assertThat(def.getTenantId()).isEqualTo(7L);
    assertThat(def.getEntityId()).isEqualTo(9001L);
    assertThat(def.getSchemaVersion()).isEqualTo(1);
    assertThat(def.getSchemaMinor()).isEqualTo(1);
    assertThat(def.getLockVersion()).isEqualTo(0L);
    assertThat(def.getFollowMode()).isEqualTo(UiSchemaDef.FOLLOW_PINNED);
  }

  @Test
  @DisplayName("空 schema 建聚合失败（fail-fast，不写脏行）")
  void create_shouldRejectBlankJson_whenEmpty() {
    assertThatThrownBy(() -> UiSchemaDef.create(7L, 9001L, "  ", 1))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("不能为空");
  }

  @Test
  @DisplayName("超 256KB 建聚合失败（MEDIUMTEXT 是上限不是目标）")
  void create_shouldRejectOversizeJson_whenOverLimit() {
    String big =
        "{\"schemaVersion\":1,\"pad\":\"" + "x".repeat(UiSchemaDef.MAX_SCHEMA_BYTES) + "\"}";
    assertThatThrownBy(() -> UiSchemaDef.create(7L, 9001L, big, 1))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("256KB");
  }

  @Test
  @DisplayName("minor 非法失败（须 ≥1）")
  void create_shouldRejectIllegalMinor_whenZero() {
    assertThatThrownBy(() -> UiSchemaDef.create(7L, 9001L, "{\"schemaVersion\":1}", 0))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("minor");
  }

  @Test
  @DisplayName("发布快照冻结草稿 + 对账版本（同事务失败即整体回滚，不半吊子）")
  void snapshotOnPublish_shouldFreezeDraftAndVersion() {
    UiSchemaDef def = UiSchemaDef.create(7L, 9001L, "{\"schemaVersion\":1}", 1);
    def.replaceDraft("{\"schemaVersion\":1,\"minor\":1}", 1);

    def.snapshotOnPublish(5);

    assertThat(def.getPublishedSchemaJson()).isEqualTo("{\"schemaVersion\":1,\"minor\":1}");
    assertThat(def.getPublishedVersion()).isEqualTo(5);
  }

  @Test
  @DisplayName("实例化写 base 指针（租户确认式跟随，永不自动推送）")
  void markFromTemplate_shouldWriteBasePointer() {
    UiSchemaDef def = UiSchemaDef.create(7L, 9001L, "{\"schemaVersion\":1}", 1);

    def.markFromTemplate("EXPENSE", "v3");

    assertThat(def.getBaseTemplateCode()).isEqualTo("EXPENSE");
    assertThat(def.getBaseVersion()).isEqualTo("v3");
    assertThat(def.getFollowMode()).isEqualTo(UiSchemaDef.FOLLOW_PINNED);
  }

  @Test
  @DisplayName("白名单常量与协议同源（18 控件 / 13 比较符 / 9 函数）")
  void whitelists_shouldMatchProtocol() {
    assertThat(UiSchemaDef.CONTROL_KINDS).hasSize(18).contains("CHILD_TABLE", "MONEY", "SLIDER");
    assertThat(UiSchemaDef.CONDITION_OPS).hasSize(13).contains("earlierThan", "between");
    assertThat(UiSchemaDef.SUBMIT_FUNCTIONS)
        .hasSize(9)
        .contains("balance", "unique", "consistency", "completeness");
  }
}
