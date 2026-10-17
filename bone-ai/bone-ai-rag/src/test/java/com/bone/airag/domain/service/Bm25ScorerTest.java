package com.bone.airag.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class Bm25ScorerTest {

  @Test
  void more_term_hits_scores_higher() {
    String query = "risk clause";
    List<String> corpus = List.of("risk risk clause", "other text");
    Bm25Scorer.CorpusStats stats = Bm25Scorer.CorpusStats.of(corpus);
    double a = Bm25Scorer.score(query, "risk risk clause", stats);
    double b = Bm25Scorer.score(query, "other text", stats);
    assertThat(a).isGreaterThan(b);
  }

  @Test
  void no_overlap_scores_zero() {
    Bm25Scorer.CorpusStats stats = Bm25Scorer.CorpusStats.of(List.of("aaa bbb"));
    assertThat(Bm25Scorer.score("zzz", "aaa bbb", stats)).isEqualTo(0.0);
  }
}
