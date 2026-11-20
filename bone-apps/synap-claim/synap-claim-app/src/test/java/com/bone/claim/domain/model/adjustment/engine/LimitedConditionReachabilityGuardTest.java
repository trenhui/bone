package com.bone.claim.domain.model.adjustment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.claim.domain.model.adjustment.engine.LiabilityRule.LimitedConditions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 不可达维度显式拒绝（工程证据底座 2.1/2.2）。
 *
 * <p><b>为什么是"显式拒绝"而不是"配了不读"</b>：V1 声明六维，当前仅医院/病种有本地事实列并可判定；区域/医保/药品/诊疗 四类要么本仓库无事实来源、要么由目录准入承载，不能在
 * {@code LimitedConditions} 里当成可判定的名单静默收下——否则
 * 配置岗填了"限某区域"却全额赔付，即"配了不生效"。本守卫用<b>白名单</b>钉死可达维度面：配置写入期枚举记录组件， 任何不在白名单的维度（例如有人新增 {@code
 * regionCodes}）立即以稳定原因码拒绝，而不是静默按无限制理算。
 */
class LimitedConditionReachabilityGuardTest {

  @Test
  @DisplayName("四类不可达维度逐一显式拒绝，原因码 LIMITED_CONDITION_DIMENSION_UNSUPPORTED")
  void rejectsUnreachableDimensions() {
    for (String dimension :
        List.of("regionCodes", "medicalInsuranceCodes", "drugCodes", "treatmentCodes")) {
      assertThatThrownBy(() -> LimitedConditions.requireReachableDimensions(Set.of(dimension)))
          .isInstanceOf(RuleGap.class)
          .hasMessageContaining("LIMITED_CONDITION_DIMENSION_UNSUPPORTED")
          .hasMessageContaining(dimension);
    }
  }

  @Test
  @DisplayName("未列入白名单的任意新维度同样拒绝（白名单而非黑名单，杜绝新增维度绕过）")
  void rejectsUnknownDimensionByWhitelist() {
    assertThatThrownBy(
            () -> LimitedConditions.requireReachableDimensions(Set.of("arbitraryNewCodes")))
        .isInstanceOf(RuleGap.class)
        .hasMessageContaining("LIMITED_CONDITION_DIMENSION_UNSUPPORTED");
  }

  @Test
  @DisplayName("已支持的两维（hospitalCodes/diagnosisCodes）正常放行，不产生拒绝")
  void allowsSupportedDimensions() {
    assertThatCode(
            () ->
                LimitedConditions.requireReachableDimensions(
                    Set.of("hospitalCodes", "diagnosisCodes")))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("空集（未配置任何限定维度）正常放行——disabled 默认行为不变")
  void allowsEmptyDimensions() {
    assertThatCode(() -> LimitedConditions.requireReachableDimensions(Set.of()))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("当前配置面全在白名单内：validateConfigShape 走通守卫，既有配置行为零变更")
  void currentConfigShapePassesGuard() {
    LiabilityRule rule =
        LiabilityRule.simple(
            "L",
            "v",
            new java.math.BigDecimal("0.8"),
            java.math.BigDecimal.ZERO,
            new java.math.BigDecimal("100"),
            true);
    assertThatCode(rule::validateConfigShape).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("门禁接线钉子：validateConfigShape 实现必须调用守卫（移除接线即翻红）")
  void guardIsWiredIntoConfigShapeValidation() {
    String src = readSource();
    int shapeIdx = src.indexOf("public void validateConfigShape()");
    assertThat(shapeIdx).isGreaterThan(-1);
    String shapeBody = src.substring(shapeIdx, Math.min(src.length(), shapeIdx + 1200));
    assertThat(shapeBody)
        .as("validateConfigShape 必须调用维度可达性守卫，杜绝'配了不读'的静默第三态")
        .contains("requireReachableDimensions");
  }

  private static String readSource() {
    Path[] candidates = {
      Path.of("src/main/java/com/bone/claim/domain/model/adjustment/engine/LiabilityRule.java"),
      Path.of(
          "bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/domain/model/adjustment/engine/LiabilityRule.java")
    };
    for (Path p : candidates) {
      if (Files.exists(p)) {
        try {
          return Files.readString(p);
        } catch (java.io.IOException e) {
          throw new IllegalStateException(e);
        }
      }
    }
    throw new IllegalStateException("无法定位 LiabilityRule.java 源码以校验门禁接线");
  }
}
