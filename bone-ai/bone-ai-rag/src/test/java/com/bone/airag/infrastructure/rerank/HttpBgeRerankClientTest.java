package com.bone.airag.infrastructure.rerank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.bone.airag.common.AiRagErrorCodes;
import com.bone.airag.domain.model.RerankCandidate;
import com.bone.airag.domain.model.RerankScored;
import com.bone.core.exception.BizException;
import java.util.List;
import org.junit.jupiter.api.Test;

class HttpBgeRerankClientTest {

  private final List<RerankCandidate> candidates =
      List.of(
          new RerankCandidate("a", "text-a", 0.0),
          new RerankCandidate("b", "text-b", 0.0),
          new RerankCandidate("c", "text-c", 0.0));

  @Test
  void parse_scores_aligns_by_index_and_defaults_missing_to_zero() {
    double[] scores =
        HttpBgeRerankClient.parseScores(
            "[{\"index\":2,\"score\":0.7},{\"index\":0,\"score\":0.9}]", 3);

    assertThat(scores).containsExactly(0.9, 0.0, 0.7);
  }

  @Test
  void parse_scores_rejects_non_array() {
    BizException ex =
        catchThrowableOfType(
            () -> HttpBgeRerankClient.parseScores("{\"x\":1}", 3), BizException.class);
    assertThat(ex.getErrorCode()).isEqualTo(AiRagErrorCodes.RERANK_PROVIDER_FAIL);
  }

  @Test
  void rank_sorts_desc_and_truncates_top_k() {
    double[] scores = {0.1, 0.9, 0.5};

    List<RerankScored> out = HttpBgeRerankClient.rank(candidates, scores, 2);

    assertThat(out).extracting(RerankScored::ref).containsExactly("b", "c");
    assertThat(out.get(0).score()).isEqualTo(0.9);
  }

  @Test
  void rank_is_stable_on_equal_scores() {
    double[] scores = {0.5, 0.5, 0.5};

    List<RerankScored> out = HttpBgeRerankClient.rank(candidates, scores, 3);

    assertThat(out).extracting(RerankScored::ref).containsExactly("a", "b", "c");
  }
}
