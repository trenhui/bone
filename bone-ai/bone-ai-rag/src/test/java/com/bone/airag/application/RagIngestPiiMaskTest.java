package com.bone.airag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.airag.application.RagApplicationService.RagIngestSegment;
import com.bone.airag.common.AiRagErrorCodes;
import com.bone.airag.domain.gateway.EmbeddingPort;
import com.bone.airag.domain.gateway.RagAclPort;
import com.bone.airag.domain.gateway.RagEventSink;
import com.bone.airag.domain.gateway.TenantProvider;
import com.bone.airag.domain.model.RagChunk;
import com.bone.airag.domain.model.RagEvent;
import com.bone.airag.domain.repository.ChunkRepository;
import com.bone.airag.domain.service.GroundingGuard;
import com.bone.airag.domain.service.NoDocAcl;
import com.bone.airag.domain.service.NoOpReranker;
import com.bone.airag.domain.service.SensitiveDataMasker;
import com.bone.core.exception.BizException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * ingest 写入侧 PII 掩码接线（2026-10-24 P0-3）：先掩码后嵌入（存什么嵌什么）+ 掩码处数事件可观测。
 *
 * <p>锁两条契约：① 落库与嵌入的都是掩码后文本——若只在出口脱敏而入库 / 嵌入用明文，存储侧仍是明文外泄面； ② 无 PII 语料不产生
 * 事件噪音（事件通道只承载数据卫生事实，避免正常流量淹没告警线）。
 */
class RagIngestPiiMaskTest {

  private final ChunkRepository repo = mock(ChunkRepository.class);
  private final EmbeddingPort embed = mock(EmbeddingPort.class);
  private final List<RagEvent> events = new ArrayList<>();

  private RagApplicationService service(SensitiveDataMasker masker) {
    when(repo.findByTenantAndDoc(42L, "doc1")).thenReturn(List.of());
    when(embed.version()).thenReturn("hash-64");
    // 桩：按入参文本条数原量返回向量（数量校验由被测方负责）
    when(embed.embedBatch(anyList()))
        .thenAnswer(
            inv -> {
              List<String> texts = inv.getArgument(0);
              return texts.stream().map(t -> new float[] {1f}).toList();
            });
    return new RagApplicationService(
        repo,
        embed,
        new NoOpReranker(),
        mock(TenantProvider.class),
        (RagAclPort) new NoDocAcl(),
        (RagEventSink) events::add,
        30,
        0.0,
        0,
        -1,
        new GroundingGuard(0.6, 0.0),
        masker,
        false);
  }

  @Test
  void ingest_masks_pii_before_embedding_and_storage() {
    service(new SensitiveDataMasker())
        .ingest(
            42L,
            "doc1",
            "v1",
            List.of(new RagIngestSegment("S1", "手机 13812345678", "clause-1", 0, 12)));

    // 嵌入的必须是掩码后文本（存什么嵌什么，检索 / 生成两侧口径一致）
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> texts = ArgumentCaptor.forClass(List.class);
    verify(embed).embedBatch(texts.capture());
    assertThat(texts.getValue()).containsExactly("手机 138****5678");

    // 落库的也必须是掩码后文本（明文不外落存储）
    ArgumentCaptor<RagChunk> saved = ArgumentCaptor.forClass(RagChunk.class);
    verify(repo).save(saved.capture());
    assertThat(saved.getValue().getText()).isEqualTo("手机 138****5678");

    // 掩码处数可观测（而非静默改写语料）
    assertThat(events)
        .extracting(RagEvent::type)
        .containsExactly(RagApplicationService.EVENT_INGEST_PII_MASKED);
    assertThat(events.get(0).detail()).contains("masked=1");
  }

  @Test
  void ingest_without_pii_emits_no_event_and_keeps_text() {
    service(new SensitiveDataMasker())
        .ingest(
            42L, "doc1", "v1", List.of(new RagIngestSegment("S1", "甲方应按时付款。", "clause-1", 0, 8)));

    ArgumentCaptor<RagChunk> saved = ArgumentCaptor.forClass(RagChunk.class);
    verify(repo).save(saved.capture());
    assertThat(saved.getValue().getText()).isEqualTo("甲方应按时付款。");
    assertThat(events).isEmpty();
  }

  @Test
  void ingest_with_masker_disabled_stores_plaintext() {
    // 合规评估后显式关闭的逃生口：原样落库（文档须记录该决策，默认形态是开启）
    service(SensitiveDataMasker.disabled())
        .ingest(
            42L,
            "doc1",
            "v1",
            List.of(new RagIngestSegment("S1", "手机 13812345678", "clause-1", 0, 12)));

    ArgumentCaptor<RagChunk> saved = ArgumentCaptor.forClass(RagChunk.class);
    verify(repo).save(saved.capture());
    assertThat(saved.getValue().getText()).isEqualTo("手机 13812345678");
    assertThat(events).isEmpty();
  }

  @Test
  void ingest_mask_failure_path_still_validates_embedding_count() {
    // 掩码不改变条数契约：provider 返回数量不符仍 fail-closed（旧版本不动）
    // 注意注册顺序：service() 内的桩按入参条数返回，须先建服务再覆写为「少返回一条」
    RagApplicationService service = service(new SensitiveDataMasker());
    when(embed.embedBatch(anyList())).thenReturn(List.of(new float[] {1f}));
    assertThatThrownBy(
            () ->
                service.ingest(
                    42L,
                    "doc1",
                    "v1",
                    List.of(
                        new RagIngestSegment("S1", "手机 13812345678", "clause-1", 0, 12),
                        new RagIngestSegment("S2", "住址北京", "clause-2", 0, 4))))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(AiRagErrorCodes.EMBEDDING_PROVIDER_FAIL));
    verify(repo, never()).save(any());
  }
}
