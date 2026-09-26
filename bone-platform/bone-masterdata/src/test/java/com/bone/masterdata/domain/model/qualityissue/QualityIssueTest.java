package com.bone.masterdata.domain.model.qualityissue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.core.exception.DomainException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** {@link QualityIssue} 纯单测：OPEN → FIXED → CLOSED 与 IGNORED 分支。 */
class QualityIssueTest {

  @Test
  void testOpenDefaultsMediumSeverity() {
    QualityIssue i =
        QualityIssue.open(1L, 10L, null, null, null, "d", null, null, LocalDateTime.now());
    assertEquals("OPEN", i.getStatus());
    assertEquals("MEDIUM", i.getSeverity());
  }

  @Test
  void testFixThenCloseFlow() {
    QualityIssue i = QualityIssue.open(1L, 10L, null, null, null, "d", "HIGH", null, null);
    assertThrows(DomainException.class, () -> i.close());
    i.fix(9L);
    assertEquals("FIXED", i.getStatus());
    i.close();
    assertEquals("CLOSED", i.getStatus());
  }

  @Test
  void testIgnoreOnlyFromOpen() {
    QualityIssue i = QualityIssue.open(1L, 10L, null, null, null, "d", null, null, null);
    i.ignore(9L);
    assertEquals("IGNORED", i.getStatus());
    assertThrows(DomainException.class, () -> i.fix(9L));
  }
}
