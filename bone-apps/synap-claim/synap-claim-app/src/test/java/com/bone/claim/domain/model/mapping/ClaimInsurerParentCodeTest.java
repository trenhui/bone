package com.bone.claim.domain.model.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * §16-3 保司主档粒度：分公司（parent_code 指向法人）的归并键契约。
 *
 * <p>主档只到法人，分公司用 {@code parent_code} 挂同一张表（不建第二套实体）。本测试锁定数据模型层的契约： 法人行的 parent_code 为空、分公司行的
 * parent_code 等于法人行的 insurer_code——这是「按 parent_code 归并到法人」 的键来源。映射解析 / 理赔归属处的实际归并逻辑位于 {@code
 * CodeMappingApplicationService}（并行会话在途）， 此处只钉死主档侧的父子键语义，避免后续改动无声地破坏「分公司属于哪家法人」的判定。
 */
class ClaimInsurerParentCodeTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void legalEntity_hasEmptyParentCode() {
    ClaimInsurer legal = ClaimInsurer.of(1L, 1L, "YCBX", "永诚财险", "永诚", "DIRECT", "", "", NOW);

    assertThat(legal.getInsurerCode()).isEqualTo("YCBX");
    assertThat(legal.getParentCode()).isEmpty();
  }

  @Test
  void branch_pointsToLegalEntityParentCode() {
    ClaimInsurer legal = ClaimInsurer.of(1L, 1L, "YCBX", "永诚财险", "永诚", "DIRECT", "", "", NOW);
    ClaimInsurer branch =
        ClaimInsurer.of(2L, 1L, "YCBX-BJ", "永诚财险北京分公司", "永诚北京", "DIRECT", "", "YCBX", NOW);

    assertThat(branch.getParentCode()).isEqualTo("YCBX");
    assertThat(branch.getParentCode()).isEqualTo(legal.getInsurerCode());
  }

  @Test
  void branchParentCode_nullNormalizedToEmpty() {
    ClaimInsurer branch =
        ClaimInsurer.of(2L, 1L, "YCBX-BJ", "永诚北京", "永诚北京", "DIRECT", "", null, NOW);

    assertThat(branch.getParentCode()).isEmpty();
  }
}
