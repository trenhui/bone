package com.bone.airag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.airag.domain.gateway.EmbeddingPort;
import com.bone.airag.domain.gateway.TenantProvider;
import com.bone.airag.domain.model.RagChunk;
import com.bone.airag.domain.model.RagQuery;
import com.bone.airag.domain.model.RagResult;
import com.bone.airag.domain.repository.ChunkRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagServiceTest {

  @Test
  void ranks_by_fused_score_and_enforces_citation() {
    ChunkRepository repo = mock(ChunkRepository.class);
    EmbeddingPort embed = mock(EmbeddingPort.class);
    TenantProvider tenant = mock(TenantProvider.class);

    // a 与查询向量同向（余弦 1）；b 正交；c 无 clauseRef（引用强制应丢弃）
    RagChunk a =
        RagChunk.of(
            1L,
            42L,
            "doc1",
            "Clause 1",
            "alpha beta",
            new float[] {1f, 0f, 0f},
            "hash-64",
            "clause-1",
            0,
            10);
    RagChunk b =
        RagChunk.of(
            2L,
            42L,
            "doc1",
            "Clause 2",
            "gamma delta",
            new float[] {0f, 1f, 0f},
            "hash-64",
            "clause-2",
            11,
            20);
    RagChunk c =
        RagChunk.of(
            3L,
            42L,
            "doc1",
            "Clause 3",
            "epsilon",
            new float[] {0f, 0f, 1f},
            "hash-64",
            null,
            21,
            30);

    when(tenant.currentTenantId()).thenReturn(42L);
    when(repo.findByTenantAndDocScope(eq(42L), any())).thenReturn(List.of(a, b, c));
    when(embed.version()).thenReturn("hash-64");
    when(embed.embed("query")).thenReturn(new float[] {1f, 0f, 0f});

    RagApplicationService service = new RagApplicationService(repo, embed, tenant);
    List<RagResult> results = service.retrieve(new RagQuery("query", null, 10));

    // c 因引用强制被丢弃；a 因余弦匹配排第一
    assertThat(results).extracting(RagResult::chunkRef).containsExactly("1", "2");
    assertThat(results.get(0).chunkRef()).isEqualTo("1");
    assertThat(results.get(0).clauseRef()).isEqualTo("clause-1");
  }
}
