package com.bone.iam.domain.gateway;

import com.bone.iam.domain.model.session.Session;
import java.util.List;
import java.util.Optional;

/**
 * Refresh-Token 视角的会话出站端口（read + revoke）。
 *
 * <p>实现见 {@code infrastructure/gateway/RefreshTokenSessionGatewayAdapter}，仅暴露不含敏感 hash 的视图。
 */
public interface RefreshTokenSessionGateway {

  /** 列出账号的活跃 + 已撤销 refresh token（默认按 created_at DESC，限制 200 条）。 */
  List<Session> listByAccountId(Long accountId);

  Optional<Session> findById(Long sessionId);

  /** 撤销单条会话，返回受影响行数。 */
  int revoke(Long sessionId);

  /** 撤销账号下全部活跃会话，返回受影响行数。 */
  int revokeAllForAccount(Long accountId);
}
