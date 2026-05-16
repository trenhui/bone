package com.bone.engine.extension.support.expression;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.engine.extension.api.exception.ExpressionEvaluationException;
import com.bone.engine.extension.support.context.BizContext;
import org.junit.jupiter.api.Test;

class SpELExpressionEvaluatorTest {

  private final SpELExpressionEvaluator evaluator = new SpELExpressionEvaluator();

  @Test
  void compileInvalidExpressionFailsFast() {
    assertThrows(ExpressionEvaluationException.class, () -> evaluator.compile("#data.["));
  }

  @Test
  void evaluateMissingPropertyAsFalse() {
    BizContext<Void> ctx = BizContext.<Void>builder().tenant("t1").build();
    assertFalse(evaluator.evaluate("#data.missing == true", ctx));
  }

  @Test
  void evaluateTrueCondition() {
    BizContext<String> ctx = BizContext.<String>builder().data("x").build();
    assertTrue(evaluator.evaluate("#data == 'x'", ctx));
  }
}
