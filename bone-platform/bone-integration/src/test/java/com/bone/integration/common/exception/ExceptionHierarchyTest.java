package com.bone.integration.common.exception;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

class ExceptionHierarchyTest {

  @Test
  void systemExceptionShouldUsePlatformSystemExceptionRoot() {
    assertInstanceOf(
        com.bone.core.exception.SystemException.class,
        new SystemException("integration unavailable"));
  }

  @Test
  void notFoundExceptionShouldUseBizExceptionRoot() {
    assertInstanceOf(
        com.bone.core.exception.BizException.class, new NotFoundException("flow not found"));
  }
}
