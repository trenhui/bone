package com.bone.claim.domain.model.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.claim.domain.model.evidence.valueobject.DocType;
import com.bone.claim.domain.model.evidence.valueobject.ImageClarity;
import com.bone.core.exception.DomainException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 影像清晰度领域行为（P0-A）：标注写入与锁定后不可改（§4.3 不变量）。
 *
 * <p>变异验证：把 markClarity 的 requireUnlocked 去掉 ⇒ 锁定后仍可改用例红；把 updatedAt 写成 Instant.now() ⇒ 钉住时间的用例红。
 */
class EvidenceClarityTest {

  private static Evidence evidence() {
    return Evidence.register(7L, 42L, 1001L, DocType.INVOICE, "oss://ref/1", "hash-1", "UNKNOWN");
  }

  @Test
  @DisplayName("登记默认 CLEAR；markClarity 写入三态与调用方注入的 updatedAt")
  void markClarityWritesClearTypeAndUpdatedAt() {
    Evidence evidence = evidence();
    assertThat(evidence.getClearType()).isEqualTo("CLEAR");

    Instant now = Instant.parse("2026-11-21T10:00:00Z");
    evidence.markClarity(ImageClarity.INCOMPLETE, now);

    assertThat(evidence.getClearType()).isEqualTo("INCOMPLETE");
    assertThat(evidence.getUpdatedAt()).isEqualTo(now);
  }

  @Test
  @DisplayName("锁定态再改清晰度被拒：不留『已锁定出金』与『事后改判清晰』拼出的放行路径")
  void markClarityAfterLockIsRejected() {
    Evidence evidence = evidence();
    Instant beforeLock = Instant.parse("2026-11-21T10:00:00Z");
    evidence.markClarity(ImageClarity.UNCLEAR, beforeLock);
    evidence.lock("op-evidence", 1);
    Instant updatedAtAfterLock = evidence.getUpdatedAt();

    assertThatThrownBy(
            () -> evidence.markClarity(ImageClarity.CLEAR, Instant.parse("2026-11-21T11:00:00Z")))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("证据已锁定，禁止改清晰度");

    assertThat(evidence.getClearType()).isEqualTo("UNCLEAR");
    assertThat(evidence.getUpdatedAt()).isEqualTo(updatedAtAfterLock);
  }

  @Test
  @DisplayName("空清晰度被拒：值域不得被 null 静默损坏")
  void nullClarityIsRejected() {
    Evidence evidence = evidence();

    assertThatThrownBy(() -> evidence.markClarity(null, Instant.now()))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("影像清晰度不能为空");
  }
}
