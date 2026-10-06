package com.bone.core.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link IdempotencyFingerprint} 的特征化测试（2026-10-05）。
 *
 * <p>该类此前<b>零测试</b>，而它是幂等语义的<b>唯一实现点</b>：指纹一旦不稳定，后果不是"报错"而是 <b>静默地重复执行</b>（重复扣款 /
 * 重复发货）——没有异常、没有日志、只有业务侧"怎么扣了两次"。
 *
 * <p><b>先纠正报告的一处因果链</b>：设计诊断P2-4 写的是「精度损失 ⇒ 指纹碰撞 ⇒ 不重复的请求被判重复 ⇒ 静默丢单」。实测Jackson 序列化包装 {@code Long}
 * 是<b>精确</b>的（输出完整十进制数），不会因超 2^53 而丢精度，所以那条链不成立。<b>真实风险方向相反</b>：指纹 = {@code SHA-256(序列化结果)}，所以
 * <b>序列化结果不稳定 ⇒ 同一请求两次得到不同指纹 ⇒ 幂等失效</b>。下面第2/ 3 条就是盯这件事。
 */
class IdempotencyFingerprintTest {

  /** 载荷：字段顺序固定的命令对象（生产里的典型形态）。 */
  record CreateOrderCmd(Long accountId, Long amount, String currency) {}

  @Test
  @DisplayName("同一对象两次调用必得同一指纹（幂等的最低要求）")
  void samePayloadYieldsSameFingerprint() {
    CreateOrderCmd cmd = new CreateOrderCmd(1001L, 2500L, "CNY");
    assertEquals(
        IdempotencyFingerprint.fingerprintOf(cmd),
        IdempotencyFingerprint.fingerprintOf(cmd),
        "同一对象两次指纹不同⇒ 幂等形同虚设（第二次请求会被当成新请求再执行一遍）");
  }

  @Test
  @DisplayName("等价但字段顺序不同的载荷 ⇒ 指纹是否相同（顺序敏感性实测）")
  void fieldOrderSensitivityIsExplicitlyKnown() {
    Map<String, Object> a = new LinkedHashMap<>();
    a.put("accountId", 1001L);
    a.put("amount", 2500L);
    Map<String, Object> b = new LinkedHashMap<>();
    b.put("amount", 2500L);
    b.put("accountId", 1001L);

    String fa = IdempotencyFingerprint.fingerprintOf(a);
    String fb = IdempotencyFingerprint.fingerprintOf(b);

    // 记录事实而非断言期望：Jackson 按迭代序序列化 Map，顺序不同 ⇒ JSON 不同 ⇒ 指纹不同。
    // 这对「同一条业务请求」而言是隐患：只要两条代码路径构造 Map 的顺序不同，就会被当成两次请求。
    // 因此本仓的幂等载荷应当用字段顺序固定的 record/DTO，而不是 Map（见类注释的落地建议）。
    assertNotEquals(
        fa,
        fb,
        "若这条变红说明 Jackson 对 Map 的序列化已按键排序（那顺序问题不存在，可删掉本断言与"
            + "类注释里的对应段落）；当前实测为不同 ⇒ Map 型载荷存在顺序敏感隐患");
    assertEquals(fa, IdempotencyFingerprint.fingerprintOf(a), "同一实例仍须稳定");
  }

  @Test
  @DisplayName("HashMap 与 LinkedHashMap 内容相同 ⇒ 指纹可能不同（顺序敏感性实测）")
  void hashMapVsLinkedHashMapIsProbed() {
    Map<String, Object> hash = new HashMap<>();
    hash.put("accountId", 1001L);
    hash.put("amount", 2500L);
    Map<String, Object> linked = new LinkedHashMap<>();
    linked.put("accountId", 1001L);
    linked.put("amount", 2500L);

    // 两种实现的迭代序可能不同 ⇒ 指纹可能不同。此处只断言「同实现内稳定」，
    // 跨实现是否相同由上面那条用例记录，不在此处强行立一个可能反复变红的期望。
    assertEquals(
        IdempotencyFingerprint.fingerprintOf(hash),
        IdempotencyFingerprint.fingerprintOf(hash),
        "HashMap 载荷自身必须稳定");
    assertEquals(
        IdempotencyFingerprint.fingerprintOf(linked),
        IdempotencyFingerprint.fingerprintOf(linked),
        "LinkedHashMap 载荷自身必须稳定");
  }

  @Test
  @DisplayName("Long 超过 2^53 仍能稳定区分（纠正『精度碰撞』说法，并锁住其反面）")
  void longBeyondJsSafeIntegerIsStable() {
    long huge1 = 9007199254740993L; // 2^53 + 1
    long huge2 = 9007199254740995L;
    CreateOrderCmd a = new CreateOrderCmd(huge1, 1L, "CNY");
    CreateOrderCmd b = new CreateOrderCmd(huge2, 1L, "CNY");

    assertEquals(
        IdempotencyFingerprint.fingerprintOf(a),
        IdempotencyFingerprint.fingerprintOf(a),
        "超 2^53 的 Long 载荷自身必须稳定");
    assertNotEquals(
        IdempotencyFingerprint.fingerprintOf(a),
        IdempotencyFingerprint.fingerprintOf(b),
        "相邻的两个超 2^53 ID 得到同一指纹 ⇒ 真碰撞，会把不同请求判成重复");
  }

  @Test
  @DisplayName("String 载荷直接按原文取指纹，不经过 JSON（少一层不确定性）")
  void stringPayloadBypassesJson() {
    String raw = "{\"accountId\":1001}";
    assertEquals(
        IdempotencyFingerprint.fingerprint(raw), IdempotencyFingerprint.fingerprintOf(raw));
  }

  @Test
  @DisplayName("null / 空白载荷归为同一空指纹")
  void nullAndBlankCollapseToEmpty() {
    String empty = IdempotencyFingerprint.fingerprintOf(null);
    assertEquals(empty, IdempotencyFingerprint.fingerprintOf(null));
    assertEquals(empty, IdempotencyFingerprint.fingerprintOf(""));
  }

  @Test
  @DisplayName("无法序列化的载荷必须抛异常，绝不降级为『空指纹』")
  void unserializablePayloadFailsLoudly() {
    Object nasty =
        new Object() {
          @SuppressWarnings("unused")
          public Object getSelf() {
            throw new IllegalStateException("boom");
          }
        };
    assertThrows(
        RuntimeException.class,
        () -> IdempotencyFingerprint.fingerprintOf(nasty),
        "序列化失败降级成空指纹 ⇒ 两个不同请求同指纹，第二次会拿到第一次的结果，" + "比不幂等更糟");
  }
}
