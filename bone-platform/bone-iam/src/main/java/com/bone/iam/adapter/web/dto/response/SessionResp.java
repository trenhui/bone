package com.bone.iam.adapter.web.dto.response;

import com.bone.iam.domain.model.session.Session;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 会话出网 DTO（替代领域实体直接序列化）。
 *
 * <p><b>为何不让 {@link Session} 直接出网</b>：领域实体是<em>内部契约</em>，字段增减不受 API 兼容性约束——一旦对外序列化，改域名即等于改 API。 历史上
 * {@code SessionController#list} 直接返回 {@code ApiResponse<List<Session>>}，等于把 {@code
 * iam_refresh_token} 的列结构（含 {@code revoked} 这类运维内部标志）固化进前端，后续领域裁剪（如拆分 token 指纹列）会静默破坏前端。
 *
 * <p><b>本 DTO 的裁剪口径</b>：只暴露运维用例（在线会话列表 / 强制下线）需要的字段，且显式排除 {@code token_hash}； {@code id / accountId
 * / tenantId} 为雪花 ID，由全局 Jackson 配置序列化为<strong>字符串</strong>（详设 §2.10），前端禁止 {@code Number()} 转换。
 */
@Data
public class SessionResp {

  private Long id;
  private Long accountId;
  private Long tenantId;
  private Boolean revoked;
  private LocalDateTime expiresAt;
  private LocalDateTime createdAt;

  /** 领域实体 → 出网 DTO。 */
  public static SessionResp from(Session session) {
    SessionResp resp = new SessionResp();
    resp.setId(session.getId());
    resp.setAccountId(session.getAccountId());
    resp.setTenantId(session.getTenantId());
    resp.setRevoked(session.isRevoked());
    resp.setExpiresAt(session.getExpiresAt());
    resp.setCreatedAt(session.getCreatedAt());
    return resp;
  }
}
