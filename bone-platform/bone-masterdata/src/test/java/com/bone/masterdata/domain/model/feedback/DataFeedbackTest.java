package com.bone.masterdata.domain.model.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.core.exception.DomainException;
import org.junit.jupiter.api.Test;

/** {@link DataFeedback} 纯单测：PENDING → ACCEPTED → DONE / REJECTED 状态机。 */
class DataFeedbackTest {

  @Test
  void testSubmitDefaultsCorrectionPending() {
    DataFeedback f = DataFeedback.submit(1L, 10L, 20L, 30L, null, "内容", null, 9L);
    assertEquals("PENDING", f.getStatus());
    assertEquals("CORRECTION", f.getFeedbackType());
    assertEquals(9L, f.getSubmittedBy());
  }

  @Test
  void testAcceptThenComplete() {
    DataFeedback f = DataFeedback.submit(1L, 10L, null, null, "ADDITION", "c", null, 9L);
    f.accept(8L);
    assertEquals("ACCEPTED", f.getStatus());
    f.complete(8L, "已采纳");
    assertEquals("DONE", f.getStatus());
  }

  @Test
  void testRejectFromPendingOnly() {
    DataFeedback f = DataFeedback.submit(1L, 10L, null, null, null, "c", null, 9L);
    f.reject(8L, "不成立");
    assertEquals("REJECTED", f.getStatus());
    assertThrows(DomainException.class, () -> f.complete(8L, "x"));
  }
}
