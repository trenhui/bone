package com.bone.airag.domain.gateway;

import java.util.ArrayList;
import java.util.List;

/** 嵌入端口（过渡：哈希占位；终局接 bge-m3 / ONNX）。消费方可 {@code @Bean} 覆盖。 */
public interface EmbeddingPort {

  /** 当前嵌入模型版本（chunk 的 {@code embedding_ver} 与之比对，不符则 fail-closed 跳过该切片向量路）。 */
  String version();

  float[] embed(String text);

  /**
   * 批量嵌入（ingest 大语料关键路径）：一次推理返回多向量，减少往返、提升 GPU 利用率。
   *
   * <p>默认实现为逐条 {@link #embed} 循环（过渡哈希、无批量语义的客户端可直接复用）；共享推理服务（TEI /
   * infinity）支持数组输入，应在实现中覆写为真正的批量请求。批返回数量必须与输入数量一致，否则调用方 fail-closed 抛错。
   */
  default List<float[]> embedBatch(List<String> texts) {
    List<float[]> out = new ArrayList<>(texts.size());
    for (String t : texts) {
      out.add(embed(t));
    }
    return out;
  }
}
