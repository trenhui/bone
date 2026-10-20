package com.bone.metadata.engine.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 运行期写校验器单测（纯策略，无 DB）。
 *
 * <p><b>判别力设计</b>：ENUM 快照同时含 {@code ACTIVE}/{@code DEPRECATED}/{@code DISABLED} 三种 state 与一个 无
 * state 的存量手写项——「全放行」退化实现红 {@code disabledRejected}，「见 state 即拒」退化实现红 {@code
 * deprecatedAndLegacyAllowed}，「不解析 state」退化实现同样红 {@code disabledRejected}。
 */
class RuntimeWriteValidatorTest {

  /** dict 物化快照：M=ACTIVE、W=DEPRECATED、X=DISABLED，外加无 state 的存量手写项 L。 */
  private static final String THREE_STATE_SNAPSHOT =
      "[{\"code\":\"M\",\"label\":\"男\",\"state\":\"ACTIVE\"},"
          + "{\"code\":\"W\",\"label\":\"女（弃用）\",\"state\":\"DEPRECATED\"},"
          + "{\"code\":\"X\",\"label\":\"未知（停用）\",\"state\":\"DISABLED\"},"
          + "{\"code\":\"L\",\"label\":\"存量手写\"}]";

  private static PublishedRuntimeEntity entity(String enumValues) {
    return new PublishedRuntimeEntity(
        "bp_employee",
        "bp_employee",
        "id",
        1L,
        List.of(
            new RuntimeFieldColumn("id", "LONG", false, false, true),
            new RuntimeFieldColumn("gender", "ENUM", true, false, false, null, enumValues, null)),
        null,
        null,
        null);
  }

  private static Map<String, Object> payload(String gender) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("gender", gender);
    return payload;
  }

  @Nested
  @DisplayName("ENUM 三态口径（字典打通 v1.1）")
  class ThreeState {

    @Test
    @DisplayName("DISABLED 选项写路径拒绝（create 与 update 双路径）")
    void disabledRejected() {
      PublishedRuntimeEntity entity = entity(THREE_STATE_SNAPSHOT);

      RuntimeRecordException onCreate =
          assertThrows(
              RuntimeRecordException.class,
              () -> RuntimeWriteValidator.validateForCreate(entity, payload("X")));
      assertEquals("META_RUNTIME_VALIDATION_FAILED", onCreate.getErrorCode());

      Map<String, Object> updatePayload = new LinkedHashMap<>();
      updatePayload.put("gender", "X");
      assertThrows(
          RuntimeException.class,
          () -> RuntimeWriteValidator.validateForUpdate(entity, updatePayload));
    }

    @Test
    @DisplayName("DEPRECATED 与无 state 的存量项放行（双口径写侧：存量数据与 API 直写仍可保存）")
    void deprecatedAndLegacyAllowed() {
      PublishedRuntimeEntity entity = entity(THREE_STATE_SNAPSHOT);

      // DEPRECATED：新表单下拉不再展示，但写路径不阻断
      RuntimeWriteValidator.validateForCreate(entity, payload("W"));
      // 无 state 的存量手写项：物化升级不改变存量契约
      RuntimeWriteValidator.validateForCreate(entity, payload("L"));
      // ACTIVE 正常放行
      RuntimeWriteValidator.validateForCreate(entity, payload("M"));
    }

    @Test
    @DisplayName("快照之外的取值拒绝（越界拦截不因三态改造退化）")
    void outOfSnapshotRejected() {
      PublishedRuntimeEntity entity = entity(THREE_STATE_SNAPSHOT);

      assertThrows(
          RuntimeException.class,
          () -> RuntimeWriteValidator.validateForCreate(entity, payload("UNKNOWN")));
    }
  }

  @Nested
  @DisplayName("既有校验语义不回归")
  class Legacy {

    @Test
    @DisplayName("纯字符串数组快照（建模者手写）按原口径放行/拒绝")
    void plainStringSnapshot() {
      PublishedRuntimeEntity entity = entity("[\"A\",\"B\"]");

      RuntimeWriteValidator.validateForCreate(entity, payload("A"));
      assertThrows(
          RuntimeException.class,
          () -> RuntimeWriteValidator.validateForCreate(entity, payload("C")));
    }

    @Test
    @DisplayName("必填缺失仍拒绝（三态改造不动必填语义）")
    void requiredStillEnforced() {
      PublishedRuntimeEntity entity = entity(THREE_STATE_SNAPSHOT);

      assertThrows(
          RuntimeException.class,
          () -> RuntimeWriteValidator.validateForCreate(entity, new LinkedHashMap<>()));
    }
  }
}
