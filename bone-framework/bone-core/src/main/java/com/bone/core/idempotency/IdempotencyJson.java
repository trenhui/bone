package com.bone.core.idempotency;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * 幂等链路专用 {@link ObjectMapper} 的单一真源。
 *
 * <p><b>为什么必须收敛到一处</b>：幂等有两个 JSON 落点——快照序列化/反序列化（{@link IdempotencyService}）与 请求体指纹计算（{@link
 * IdempotencyFingerprint}）。二者一旦各持一个裸 {@code new ObjectMapper()}，就会 重复踩同一个坑：命令里带 {@code
 * java.time.Instant}（理赔 {@code SubmitClaim.outInsureTime} 即是）时 序列化直接抛 {@code
 * InvalidDefinitionException}，表现为报案 500。2026-10-25 联调先后在快照与指纹两处 各踩一次，故合并。
 *
 * <p><b>三项配置的理由</b>：
 *
 * <ul>
 *   <li>{@code JavaTimeModule}：JSR-310 类型（Instant/LocalDate…）的开箱支持；
 *   <li>{@code WRITE_DATES_AS_TIMESTAMPS=false}：快照与指纹都用 ISO-8601 字符串——人工可读，且指纹跨机器稳定
 *       （时间戳数组虽也可比，但语义弱、排障时不可读）；
 *   <li>{@code FAIL_ON_UNKNOWN_PROPERTIES=false}：快照反序列化容忍字段演进（新增字段不炸旧快照）。
 * </ul>
 *
 * <p>{@code ObjectMapper} 配置完成后线程安全，故静态共享单例即可，无需每实例新建。
 */
final class IdempotencyJson {

  static final ObjectMapper MAPPER =
      new ObjectMapper()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private IdempotencyJson() {}
}
