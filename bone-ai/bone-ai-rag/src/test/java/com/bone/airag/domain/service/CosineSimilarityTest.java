package com.bone.airag.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class CosineSimilarityTest {

  private static final Offset<Double> EPS = Offset.offset(1e-9);

  @Test
  void identical_vectors_cosine_is_one() {
    assertThat(CosineSimilarity.of(new float[] {1, 2, 3}, new float[] {1, 2, 3}))
        .isCloseTo(1.0, EPS);
  }

  @Test
  void orthogonal_vectors_cosine_is_zero() {
    assertThat(CosineSimilarity.of(new float[] {1, 0}, new float[] {0, 1})).isCloseTo(0.0, EPS);
  }

  @Test
  void opposite_vectors_cosine_is_minus_one() {
    assertThat(CosineSimilarity.of(new float[] {1, 0}, new float[] {-1, 0})).isCloseTo(-1.0, EPS);
  }

  @Test
  void mismatched_length_is_zero() {
    assertThat(CosineSimilarity.of(new float[] {1}, new float[] {1, 2})).isEqualTo(0.0);
  }
}
