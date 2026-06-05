package com.bone.metadata.engine.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RuntimePageQueryTest {

  @Test
  void parseSortAndFields() {
    RuntimePageQuery q = RuntimePageQuery.parse("order_no,amount", "-amount,id", null);
    assertEquals(2, q.selectFields().size());
    assertEquals("amount", q.sortSpecs().get(0).field());
    assertEquals(true, q.sortSpecs().get(0).descending());
  }

  @Test
  void parseFilterRejectsInvalidQ() {
    assertThrows(RuntimeRecordException.class, () -> RuntimePageQuery.parse(null, null, "bad"));
  }
}
