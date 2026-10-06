package com.bone.integration.application.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.integration.infrastructure.security.AesConnectorSecretCipherAdapter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 连接器凭据处理判据（P2-2，2026-10-05）。
 *
 * <p>三条不可退让的底线，任何一条破了都算回归：
 *
 * <ol>
 *   <li><b>读端不得出现凭据明文</b> —— 详情/列表都走 {@link ConnectorSecretSupport#maskForRead}；
 *   <li><b>编辑一次不得清空凭据</b> —— 前端把详情 JSON 原样回填再提交，所以入参缺凭据键时 **必须**沿用库中原值（见 ConnectorManagement.tsx 的
 *       {@code JSON.stringify(connector.config)}）；
 *   <li><b>存量明文必须继续可用</b> —— 加密只对新写入生效，解密侧对无前缀值原样返回，存量连接器 不能因为这次安全加固而集体失效。
 * </ol>
 */
class ConnectorSecretSupportTest {

  private static final String SECRET = "super-secret-value";

  private static ConnectorSecretSupport supportWith(String key) {
    return new ConnectorSecretSupport(new AesConnectorSecretCipherAdapter(key));
  }

  private static Map<String, Object> config(String secret) {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("endpoint", "https://s3.example.com");
    config.put("bucket", "my-bucket");
    config.put("accessKey", "AKIA-not-a-secret");
    config.put("secretKey", secret);
    return config;
  }

  @Test
  @DisplayName("读端剔除凭据明文，但保留定位信息并回报已配置的键名")
  void readNeverLeaksSecretPlaintext() {
    ConnectorSecretSupport.MaskedConfig masked =
        supportWith("test-key-32-bytes-xxxxxxxxxxxxxxx").maskForRead(config(SECRET));

    assertFalse(
        masked.config().containsKey("secretKey"), "详情/列表仍回吐 secretKey ⇒ 任何有读权限的账号都能批量导出全平台第三方凭据");
    assertEquals(SECRET, "super-secret-value"); // 防止测试自身写错断言
    assertEquals("https://s3.example.com", masked.config().get("endpoint"));
    assertEquals("AKIA-not-a-secret", masked.config().get("accessKey"), "accessKey 是定位信息，不该被剔除");
    assertTrue(
        masked.secretKeysConfigured().contains("secretKey"), "未回报已配置的凭据键名 ⇒ 前端无法显示「已配置」，只能让用户重新输入");
  }

  @Test
  @DisplayName("入参缺凭据键时沿用库中原值（前端回填后保存的必然是这个形态）")
  void updateKeepsExistingSecretWhenAbsentFromIncoming() {
    Map<String, Object> incoming = new LinkedHashMap<>();
    incoming.put("endpoint", "https://s3.example.com"); // 详情回填：没有 secretKey
    incoming.put("bucket", "new-bucket");

    Map<String, Object> merged =
        supportWith("test-key-32-bytes-xxxxxxxxxxxxxxx").mergeOnUpdate(incoming, config(SECRET));

    assertEquals(SECRET, effectiveSecret(merged), "凭据被清空 ⇒ 用户改个 bucket 就把连接器弄失效，这是本项加固最容易造成的事故");
    assertEquals("new-bucket", merged.get("bucket"), "非凭据键应以待入参为准");
  }

  @Test
  @DisplayName("入参是掩码占位符时同样沿用库中原值")
  void updateKeepsExistingSecretWhenIncomingIsPlaceholder() {
    Map<String, Object> incoming = new LinkedHashMap<>();
    incoming.put("secretKey", "******");

    Map<String, Object> merged =
        supportWith("test-key-32-bytes-xxxxxxxxxxxxxxx").mergeOnUpdate(incoming, config(SECRET));

    assertEquals(SECRET, effectiveSecret(merged), "掩码值被当成真值写回 ⇒ 凭据永久丢失");
  }

  @Test
  @DisplayName("入参给了新凭据则取新值")
  void updateTakesNewSecretWhenProvided() {
    Map<String, Object> incoming = new LinkedHashMap<>();
    incoming.put("secretKey", "rotated-secret");

    Map<String, Object> merged =
        supportWith("test-key-32-bytes-xxxxxxxxxxxxxxx").mergeOnUpdate(incoming, config(SECRET));

    assertEquals("rotated-secret", effectiveSecret(merged), "轮换凭据必须能生效，否则用户永远换不掉密钥");
  }

  @Test
  @DisplayName("加密写入 + 解密消费可往返，且不会把 accessKey 误当凭据")
  void encryptThenDecryptRoundTrips() {
    ConnectorSecretSupport support = supportWith("test-key-32-bytes-xxxxxxxxxxxxxxx");

    Map<String, Object> persisted = support.encryptForPersist(config(SECRET));
    assertNotEqualsPlaintext(SECRET, String.valueOf(persisted.get("secretKey")));
    assertEquals("AKIA-not-a-secret", persisted.get("accessKey"), "accessKey 不应被加密，否则客户端取不到");

    Map<String, Object> consumed = support.decryptForConsume(persisted);
    assertEquals(SECRET, consumed.get("secretKey"), "消费侧拿不到真实凭据 ⇒ 连接器全部连不上");
  }

  @Test
  @DisplayName("存量明文（无版本前缀）解密时原样返回，加密时不二次加密")
  void legacyPlaintextStaysUsable() {
    ConnectorSecretSupport support = supportWith("test-key-32-bytes-xxxxxxxxxxxxxxx");

    Map<String, Object> consumed = support.decryptForConsume(config(SECRET));
    assertEquals(SECRET, consumed.get("secretKey"), "存量明文解密后变了 ⇒ 上线即所有连接器失效");

    String once =
        new AesConnectorSecretCipherAdapter("test-key-32-bytes-xxxxxxxxxxxxxxx").encrypt(SECRET);
    String twice =
        new AesConnectorSecretCipherAdapter("test-key-32-bytes-xxxxxxxxxxxxxxx").encrypt(once);
    assertEquals(once, twice, "密文被二次加密 ⇒ 消费侧只能解一层，凭据不可用");
  }

  @Test
  @DisplayName("未配置加密密钥时降级为明文，但读端依然不返回凭据")
  void degradesToPlaintextWithoutKey() {
    ConnectorSecretSupport support = supportWith("");

    Map<String, Object> persisted = support.encryptForPersist(config(SECRET));
    assertEquals(SECRET, persisted.get("secretKey"), "未配密钥时应保持原行为（明文），而不是把凭据写坏");

    assertFalse(
        support.maskForRead(persisted).config().containsKey("secretKey"),
        "即使没配加密密钥，读端也必须剔除凭据 —— 这才是本项加固的主要防线");
  }

  @Test
  @DisplayName("配置了密钥但密文来自别的密钥时解密失败关闭（返回 null，不吐垃圾）")
  void decryptFailsClosedOnWrongKey() {
    String cipherText =
        new AesConnectorSecretCipherAdapter("key-a-32-bytes-xxxxxxxxxxxxx").encrypt(SECRET);
    assertNull(
        new AesConnectorSecretCipherAdapter("key-b-32-bytes-xxxxxxxxxxxxx").decrypt(cipherText),
        "密钥不匹配却返回了明文/垃圾 ⇒ 会把错误凭据发往第三方，错误现场彻底丢失");
  }

  @Test
  @DisplayName("敏感键名单大小写不敏感，且不误伤定位信息")
  void secretKeyMatchingIsCaseInsensitiveAndNarrow() {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("SecretKey", SECRET);
    config.put("PASSWORD", SECRET);
    config.put("username", "admin");
    config.put("endpoint", "https://x.example.com");

    ConnectorSecretSupport.MaskedConfig masked =
        supportWith("test-key-32-bytes-xxxxxxxxxxxxxxx").maskForRead(config);

    assertEquals(
        List.of("SecretKey", "PASSWORD"),
        masked.secretKeysConfigured(),
        "大小写不同的凭据键未被识别 ⇒ 换个大小写就能绕过脱敏");
    assertTrue(masked.config().containsKey("username"));
    assertTrue(masked.config().containsKey("endpoint"));
  }

  /** {@code mergeOnUpdate} 的返回值是**待落库**形态（凭据已加密），所以断言必须经解密后比对， 否则测的是「密文等于明文」这种永远不成立的东西。 */
  private static String effectiveSecret(Map<String, Object> persisted) {
    return String.valueOf(
        supportWith("test-key-32-bytes-xxxxxxxxxxxxxxx")
            .decryptForConsume(persisted)
            .get("secretKey"));
  }

  private static void assertNotEqualsPlaintext(String secret, String actual) {
    assertFalse(secret.equals(actual), "落库值仍是明文 ⇒ 加密没生效（读端脱敏只是遮住了 UI，库里仍可被导出）");
  }
}
