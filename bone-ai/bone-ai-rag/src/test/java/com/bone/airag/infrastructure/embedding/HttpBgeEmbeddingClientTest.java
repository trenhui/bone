package com.bone.airag.infrastructure.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.bone.airag.common.AiRagErrorCodes;
import com.bone.core.exception.BizException;
import org.junit.jupiter.api.Test;

class HttpBgeEmbeddingClientTest {

  @Test
  void parses_first_embedding_vector() {
    float[] v = HttpBgeEmbeddingClient.parseFirstEmbedding("[[0.1,0.2,-0.3],[0.9,0.9,0.9]]");

    assertThat(v).containsExactly(0.1f, 0.2f, -0.3f);
  }

  @Test
  void rejects_missing_vector_array() {
    BizException ex =
        catchThrowableOfType(
            () -> HttpBgeEmbeddingClient.parseFirstEmbedding("[]"), BizException.class);
    assertThat(ex.getErrorCode()).isEqualTo(AiRagErrorCodes.EMBEDDING_PROVIDER_FAIL);
  }

  @Test
  void version_comes_from_configuration() {
    HttpBgeEmbeddingClient client =
        new HttpBgeEmbeddingClient("http://tei:80", "BAAI/bge-m3", "bge-m3@http", null);

    assertThat(client.version()).isEqualTo("bge-m3@http");
  }
}
