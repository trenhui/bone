package com.bone.airag.infrastructure.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.airag.domain.service.CosineSimilarity;
import org.junit.jupiter.api.Test;

class TransitionalHashEmbeddingTest {

  @Test
  void chinese_query_matches_related_clause_better_than_unrelated() {
    // 2026-10-24 分词修正回归：原空白切分下中文整句=单 token，向量路失效。
    // 现复用 CjkTokenizer（CJK bigram），相关中文文本应比无关文本相似度更高。
    TransitionalHashEmbedding embed = new TransitionalHashEmbedding();
    float[] query = embed.embed("逾期付款违约金");
    float[] related = embed.embed("甲方逾期付款应支付违约金");
    float[] unrelated = embed.embed("保密期限为五年");

    double relatedSim = CosineSimilarity.of(query, related);
    double unrelatedSim = CosineSimilarity.of(query, unrelated);

    assertThat(relatedSim).isGreaterThan(0.0);
    assertThat(relatedSim).isGreaterThan(unrelatedSim);
  }

  @Test
  void version_reflects_cjk_tokenization() {
    assertThat(new TransitionalHashEmbedding().version()).isEqualTo("hash-64-cjk");
  }
}
