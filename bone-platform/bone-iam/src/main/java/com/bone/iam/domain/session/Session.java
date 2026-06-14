package com.bone.iam.domain.session;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

/**
 * 域内会话值对象（脱敏自 {@code iam_refresh_token}，不含 token_hash）。
 *
 * <p>用于运维"在线会话列表 / 强制下线"用例（详设 §3.4 / IAM-21）。出现在 application 与 adapter 层时直接序列化为 JSON 即可；列内含 {@code
 * revoked} 标志便于历史排查。
 */
@Value
@Builder
public class Session {

  Long id;
  Long accountId;
  Long tenantId;
  boolean revoked;
  LocalDateTime expiresAt;
  LocalDateTime createdAt;
}
