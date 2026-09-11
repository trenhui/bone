package com.bone.metadata.sdk.query.criteria;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.annotation.ReadSideOnly;
import org.junit.jupiter.api.Test;

class CriteriaReadSideOnlyTest {

  @Test
  void criteriaShouldBeMarkedAsReadSideOnly() {
    assertTrue(
        Criteria.class.isAnnotationPresent(ReadSideOnly.class),
        "Criteria 是查询 DSL，必须声明 @ReadSideOnly，避免进入写用例");
  }
}
