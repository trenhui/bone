package com.bone.airag.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.airag.domain.model.RerankCandidate;
import com.bone.airag.domain.model.RerankScored;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoOpRerankerTest {

  private final NoOpReranker reranker = new NoOpReranker();

  @Test
  void preserves_input_order_and_prior_scores() {
    List<RerankCandidate> candidates =
        List.of(
            new RerankCandidate("a", "text-a", 0.9),
            new RerankCandidate("b", "text-b", 0.4),
            new RerankCandidate("c", "text-c", 0.1));

    List<RerankScored> out = reranker.rerank("q", candidates, 3);

    assertThat(out).extracting(RerankScored::ref).containsExactly("a", "b", "c");
    assertThat(out).extracting(RerankScored::score).containsExactly(0.9, 0.4, 0.1);
  }

  @Test
  void truncates_to_top_k() {
    List<RerankCandidate> candidates =
        List.of(
            new RerankCandidate("a", "x", 0.9),
            new RerankCandidate("b", "y", 0.4),
            new RerankCandidate("c", "z", 0.1));

    List<RerankScored> out = reranker.rerank("q", candidates, 2);

    assertThat(out).extracting(RerankScored::ref).containsExactly("a", "b");
  }

  @Test
  void empty_candidates_returns_empty() {
    assertThat(reranker.rerank("q", List.of(), 5)).isEmpty();
    assertThat(reranker.rerank("q", null, 5)).isEmpty();
  }
}
