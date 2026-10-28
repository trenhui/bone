package com.bone.metadata.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * dev / 联调「开箱即用」契约：本服务必须能校验 IAM 签发的令牌。
 *
 * <p><b>为什么需要这个测试</b>（2026-10-28 全模块联调实测确诊）：IAM 的 dev 默认密钥是 {@code
 * dev-only-secret-key-minimum-32-bytes-long}（见 bone-iam 的 application.yml），而本服务两个密钥键原本都回落为
 * <b>空串</b>。空串会让 {@link JwtUtil#init()} 走到「每次启动随机密钥」分支 —— 本实例只能自签自验， 于是<b>所有 IAM 令牌验签失败 →
 * 请求被匿名化</b>。表现为：审批中心「我的待办 / 我已审批」 （以登录用户为办理人检索）恒 403「匿名主体没有待办」，而其余不依赖 {@code CurrentUserProvider}
 * 的接口 （dev 关闭安全、匿名主体带全量权限）照常 200 —— 现象与「权限配错」无法区分，极难定位。
 *
 * <p>本测试守住的是<b>配置默认值</b>而非某个实现类：把 application.yaml 的回落改回空串，或改成与 IAM 不同的 字面量，本测试立刻转红。
 */
class JwtSecretDevParityTest {

  /** 与 bone-iam 的 dev 默认密钥同源；两处必须逐字一致，否则 dev 联调必然匿名化。 */
  private static final String IAM_DEV_DEFAULT_SECRET = "dev-only-secret-key-minimum-32-bytes-long";

  @Test
  void dev默认密钥与IAM同源且可校验IAM令牌() {
    String raw = yamlValue("bone.jwt.secret");
    assertNotNull(raw, "application.yaml 必须声明 bone.jwt.secret");

    // 1) 文本契约：回落字面量必须与 IAM 的 dev 默认逐字一致（改回 ${BONE_JWT_SECRET:} 即红）
    assertTrue(
        raw.contains(IAM_DEV_DEFAULT_SECRET), "bone.jwt.secret 的 dev 回落必须是 IAM 同源的默认密钥，实际为：" + raw);

    // 2) 真实执行：用解析后的密钥签发 → JwtUtil 必须能验过（而不是随机密钥）
    String secret = new StandardEnvironment().resolvePlaceholders(raw);
    assertNotNull(secret, "占位符解析失败：" + raw);
    assertFalse(secret.isBlank(), "bone.jwt.secret 解析后不得为空（空串 → 随机密钥 → 令牌被匿名化）");
    assertTrue(
        secret.getBytes(StandardCharsets.UTF_8).length >= 32,
        "HS256 需要 ≥32 字节，实际：" + secret.length());

    String token =
        Jwts.builder()
            .subject("admin")
            .claims(Map.of("userId", "1"))
            .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
            .compact();

    JwtUtil util = new JwtUtil();
    ReflectionTestUtils.setField(util, "jwtSecret", secret);
    util.init();

    Claims claims = util.parseToken(token);
    assertEquals("admin", claims.getSubject());
    assertEquals("1", claims.get("userId"));
  }

  /** 反向自证：密钥为空时回退随机密钥，IAM 同密钥签发的令牌<b>验不过</b>——即生产故障现场。 */
  @Test
  void 密钥为空时回退随机密钥_无法校验IAM令牌() {
    String token =
        Jwts.builder()
            .subject("admin")
            .signWith(Keys.hmacShaKeyFor(IAM_DEV_DEFAULT_SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();

    JwtUtil util = new JwtUtil();
    ReflectionTestUtils.setField(util, "jwtSecret", "");
    util.init();

    try {
      util.parseToken(token);
      throw new AssertionError("空密钥竟能校验令牌：回退分支失效，故障将再次被掩盖");
    } catch (io.jsonwebtoken.JwtException expected) {
      // 期望：随机密钥验不过 IAM 令牌 —— 这正是「匿名主体」的成因
    }
  }

  /** 读取本模块 application.yaml 的原始（未解析占位符）配置值。 */
  private static String yamlValue(String key) {
    YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
    yaml.setResources(new ClassPathResource("application.yaml"));
    yaml.afterPropertiesSet();
    java.util.Properties props = yaml.getObject();
    return props == null ? null : props.getProperty(key);
  }
}
