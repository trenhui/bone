package com.bone.claim;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 后督抽样候选 SQL 的终态范围契约守卫（V1.0 100% 全量覆盖）。
 *
 * <p><b>为什么需要这道门禁</b>：自动抽样候选来自 {@code findFinishedForInspectionAllTenants.sql}。现网该 SQL 写死 {@code
 * WHERE c.status = 'FINISHED'}，拒赔终态（{@code CLOSED_REJECTED}）被静默排除——这使 PRD §14 V1.0 「自动通过 +
 * 拒赔结论全量覆盖」在 100% 配置下也无法满足：拒赔案件永远不会进入抽检候选。
 *
 * <p><b>为什么下游不改</b>：{@code sampleIfEligible} 的终态门槛 {@code claim.finished()} 已经接受 {@code FINISHED} 与
 * {@code CLOSED_REJECTED}（见 {@code ClaimStatus.terminal()}），因此拒赔案件的建单链路是现成的， 唯一缺口是候选 SQL
 * 的状态过滤。本测试钉住该 SQL 必须覆盖两个终态。
 *
 * <p><b>变异可证伪</b>：把 SQL 终态白名单回退为仅 {@code FINISHED}，本测试必红；还原必绿。
 */
class InspectionSamplingTerminalScopeTest {

  /** 与 SqlTemplateFirstLineTest 一致的 SQL 根目录约定。 */
  private static final Path SQL_FILE =
      Path.of(
          "src/main/resources/sql/com/bone/claim/domain/repository/ClaimCaseRepository/findFinishedForInspectionAllTenants.sql");

  @Test
  @DisplayName("候选 SQL 必须同时覆盖 FINISHED 与 CLOSED_REJECTED 两个终态（100% 全量覆盖的终态前置）")
  void candidateSqlCoversBothTerminals() throws IOException {
    assertThat(Files.isRegularFile(SQL_FILE))
        .as("候选 SQL 模板不存在 —— 路径变更会让本门禁恒过/失效，需同步更新本测试")
        .isTrue();

    String sql = Files.readString(SQL_FILE, StandardCharsets.UTF_8);
    String normalized = sql.replaceAll("\\s+", " ").toUpperCase(java.util.Locale.ROOT);

    assertThat(normalized)
        .as("自动抽样候选 SQL 必须纳入拒赔终态 CLOSED_REJECTED，" + "否则即使配置 100% 也无法覆盖拒赔结论（PRD §14 V1.0 口径）")
        .contains("CLOSED_REJECTED");
    assertThat(normalized).as("FINISHED（自动通过/已支付终态）必须保留，不能因扩展终态而丢失现网覆盖").contains("FINISHED");
  }

  @Test
  @DisplayName("下游终态门槛 claim.finished() 必须兼容拒赔（ClaimStatus.terminal 含 CLOSED_REJECTED）")
  void downstreamTerminalGateAcceptsRejected() {
    // 若有人把 terminal() 改回只认 FINISHED，候选 SQL 虽纳入拒赔，sampleIfEligible 仍会拒绝建单。
    assertThat(ClaimStatus.FINISHED.terminal()).isTrue();
    assertThat(ClaimStatus.CLOSED_REJECTED.terminal())
        .as("拒赔必须被认定终态，否则 100% 配置下拒赔案件进候选也被下游门槛挡掉")
        .isTrue();
    assertThat(ClaimStatus.REVIEWING.terminal()).as("非终态不得被误纳入，防止抽样池混入中间态").isFalse();
  }

  @Test
  @DisplayName("终态扩展后必须保留 NOT EXISTS 去重（拒赔入候选也不能重复建抽检单）")
  void candidateSqlKeepsDedupGuard() throws IOException {
    String sql = Files.readString(SQL_FILE, StandardCharsets.UTF_8);
    String normalized = sql.replaceAll("\\s+", " ").toUpperCase(java.util.Locale.ROOT);

    assertThat(normalized)
        .as("NOT EXISTS 去重必须保留：拒赔案件纳入候选后，若去除去重会对已抽检案件重复建单")
        .contains("NOT EXISTS");
    assertThat(normalized)
        .as("去重子查询须指向 t_claim_inspection，防止去重目标被改错")
        .contains("T_CLAIM_INSPECTION");
  }
}
