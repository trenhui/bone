package com.bone.airag.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class Bm25ScorerTest {

  @Test
  void more_term_hits_scores_higher() {
    String query = "risk clause";
    double a =
        Bm25Scorer.score(query, "risk risk clause", List.of("risk risk clause", "other text"));
    double b = Bm25Scorer.score(query, "other text", List.of("risk risk clause", "other text"));
    assertThat(a).isGreaterThan(b);
  }

  @Test
  void no_overlap_scores_zero() {
    assertThat(Bm25Scorer.score("zzz", "aaa bbb", List.of("aaa bbb"))).isEqualTo(0.0);
  }
}
