package com.bone.engine.extension.studio.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.engine.extension.studio.infrastructure.gateway.SharedSecretReporterTokenValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 上报令牌校验器（{@link SharedSecretReporterTokenValidator}）的行为锁定。
 *
 * <p><b>本类保护的是一条配置默认值，不是业务逻辑</b>：安全配置的默认必须是「拒绝」。 若把 {@code reporterAuthRequired} 的默认值改成 {@code
 * false}，代码能编译、所有测试 仍能过（因为放行只在 required=false 分支发生），但生产漏配密钥时会静默退化为匿名可写 —— 这类回归不会被任何既有测试发现，故在此显式锁定。
 */
class SharedSecretReporterTokenValidatorTest {

  private static final String TOKEN = "s3cr3t-reporter-token";

  private static ExtensionStudioProperties props(String token, boolean required) {
    ExtensionStudioProperties p = new ExtensionStudioProperties();
    p.getSecurity().setReporterToken(token);
    p.getSecurity().setReporterAuthRequired(required);
    return p;
  }

  @Test
  @DisplayName("默认配置即失败关闭（reporterAuthRequired 默认 true —— 漏配密钥应拒绝而非放行）")
  void defaultsToFailClosed() {
    ExtensionStudioProperties defaults = new ExtensionStudioProperties();

    assertTrue(
        defaults.getSecurity().isReporterAuthRequired(),
        "安全配置的默认必须是「要求配置密钥」；改成 false 会让生产漏配静默退化为匿名可写");
  }

  @Test
  @DisplayName("未配置密钥 + 要求校验 → 拒绝一切令牌（上报方带令牌也拒绝）")
  void rejectsEverythingWhenNotConfiguredAndRequired() {
    SharedSecretReporterTokenValidator v = new SharedSecretReporterTokenValidator(props("", true));

    assertFalse(v.isConfigured());
    assertFalse(v.isAcceptable(null), "未配置密钥时不能因为「请求没带令牌」而放行");
    assertFalse(v.isAcceptable(""), "空串同理");
    assertFalse(v.isAcceptable(TOKEN), "服务端没密钥可比对；此时放行等于恢复匿名可写 —— 绝不能因为上报方带了任意值就放行");
  }

  @Test
  @DisplayName("未配置密钥 + 显式放开（本地联调）→ 放行且 isConfigured=false（供 filter 区分 503）")
  void allowsWhenExplicitlyRelaxed() {
    SharedSecretReporterTokenValidator v = new SharedSecretReporterTokenValidator(props("", false));

    assertFalse(v.isConfigured());
    assertTrue(v.isAcceptable(null));
  }

  @Test
  @DisplayName("已配置密钥 → 只接受完全一致的令牌")
  void acceptsOnlyExactTokenWhenConfigured() {
    SharedSecretReporterTokenValidator v =
        new SharedSecretReporterTokenValidator(props(TOKEN, true));

    assertTrue(v.isConfigured());
    assertTrue(v.isAcceptable(TOKEN));
    assertFalse(v.isAcceptable("wrong"));
    assertFalse(v.isAcceptable(null));
    assertFalse(v.isAcceptable(""));
  }

  @Test
  @DisplayName("配置项前后的空白不应影响匹配（yml 里对齐/换行很容易带上空白）")
  void toleratesSurroundingWhitespace() {
    SharedSecretReporterTokenValidator v =
        new SharedSecretReporterTokenValidator(props("  " + TOKEN + "\n", true));

    assertTrue(v.isConfigured());
    assertTrue(v.isAcceptable(TOKEN), "配置值带空白、上报值不带，应能匹配");
    assertTrue(v.isAcceptable("  " + TOKEN), "上报值带空白也应能匹配");
  }

  @Test
  @DisplayName("仅空白的配置值视为未配置（不能因'有值但全空白'绕过失败关闭）")
  void treatsBlankTokenAsNotConfigured() {
    SharedSecretReporterTokenValidator v =
        new SharedSecretReporterTokenValidator(props("   ", true));

    assertFalse(v.isConfigured(), "全空白不是有效密钥；若当作已配置，比较会退化成「空白 == 空白」而可被绕过");
    assertFalse(v.isAcceptable("anything"));
  }

  @Test
  @DisplayName("令牌长度不影响正确性（覆盖单字符与超长两个极端）")
  void handlesEdgeLengths() {
    SharedSecretReporterTokenValidator shortToken =
        new SharedSecretReporterTokenValidator(props("a", true));
    assertTrue(shortToken.isAcceptable("a"));
    assertFalse(shortToken.isAcceptable("b"));

    String longToken = "x".repeat(512);
    SharedSecretReporterTokenValidator longCfg =
        new SharedSecretReporterTokenValidator(props(longToken, true));
    assertTrue(longCfg.isAcceptable(longToken));
    assertFalse(longCfg.isAcceptable(longToken + "y"), "多一个字符即应拒绝（无前缀匹配漏洞）");
  }
}
