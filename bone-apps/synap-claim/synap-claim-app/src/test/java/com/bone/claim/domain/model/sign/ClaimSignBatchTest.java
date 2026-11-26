package com.bone.claim.domain.model.sign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.claim.domain.model.sign.valueobject.SignChannel;
import com.bone.claim.domain.model.sign.valueobject.SignSource;
import com.bone.claim.domain.model.sign.valueobject.SignStatus;
import com.bone.claim.domain.model.sign.valueobject.SignType;
import com.bone.core.exception.DomainException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClaimSignBatchTest {

  private static ClaimSignBatch newBatch() {
    return ClaimSignBatch.create(
        1L,
        10L,
        "SG202611250001",
        SignSource.OFFLINE,
        SignType.MANUAL,
        SignChannel.EXPRESS,
        null,
        "SF",
        "SF123",
        "张三",
        Instant.now());
  }

  @Nested
  @DisplayName("四态状态机")
  class Transitions {
    @Test
    @DisplayName("合法四步流转")
    void legalFlow() {
      ClaimSignBatch b = newBatch();
      assertThat(b.status()).isEqualTo(SignStatus.WAIT_FOR_PARTICIPANT);
      assertThat(b.transitionTo(SignStatus.WAIT_FOR_IMAGE, "UPLOAD_PARTICIPANT", "u1")).isTrue();
      assertThat(b.transitionTo(SignStatus.WAIT_FOR_CONFIRMATION, "UPLOAD_IMAGE", "u1")).isTrue();
      assertThat(b.transitionTo(SignStatus.FINISHED, "CONFIRM", "u1")).isTrue();
      assertThat(b.status()).isEqualTo(SignStatus.FINISHED);
    }

    @Test
    @DisplayName("跳过前置步骤被拒绝")
    void skipStepRejected() {
      ClaimSignBatch b = newBatch();
      assertThatThrownBy(() -> b.transitionTo(SignStatus.FINISHED, "CONFIRM", "u1"))
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("非法跃迁");
    }

    @Test
    @DisplayName("终态再次签收被拒绝")
    void terminalRejected() {
      ClaimSignBatch b = newBatch();
      b.transitionTo(SignStatus.WAIT_FOR_IMAGE, "x", "u1");
      b.transitionTo(SignStatus.WAIT_FOR_CONFIRMATION, "x", "u1");
      b.transitionTo(SignStatus.FINISHED, "x", "u1");
      assertThatThrownBy(() -> b.transitionTo(SignStatus.FINISHED, "CONFIRM", "u1"))
          .isInstanceOf(DomainException.class);
    }
  }

  @Nested
  @DisplayName("影像完备与签收意见")
  class Guards {
    @Test
    @DisplayName("明细影像未齐时拒绝确认")
    void imagesNotReadyRejected() {
      ClaimSignBatch b = newBatch();
      b.addItem(SignBatchItem.of(10L, 1L, 100L, "CL001"));
      b.addItem(SignBatchItem.of(10L, 1L, 101L, "CL002"));
      b.registerImageReady();
      assertThatThrownBy(b::requireImagesReady).isInstanceOf(DomainException.class);
      b.registerImageReady();
      b.requireImagesReady();
    }

    @Test
    @DisplayName("签收意见少于10字被拒绝")
    void shortRemarkRejected() {
      ClaimSignBatch b = newBatch();
      assertThatThrownBy(() -> b.completeSign("u1", "太短", Instant.now()))
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("10字");
      b.completeSign("u1", "影像齐全，确认签收完成", Instant.now());
      assertThat(b.getSignRemark()).isEqualTo("影像齐全，确认签收完成");
    }
  }
}
