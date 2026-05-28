package com.bone.iam.application.query.handler;

import com.bone.core.tenant.context.TenantContext;
import com.bone.iam.domain.gateway.RefreshTokenSessionStore;
import com.bone.iam.domain.session.Session;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 列出账号活跃 / 历史 refresh token。 */
@Component
@RequiredArgsConstructor
public class SessionListQueryHandler {

    private final RefreshTokenSessionStore sessionStore;

    @Transactional(readOnly = true)
    public List<Session> handle(Long accountId) {
        List<Session> sessions = sessionStore.listByAccountId(accountId);
        Long current = TenantContext.getTenantId();
        if (current == null || current == 0L) {
            return sessions;
        }
        // 非平台租户只能看到本租户的会话；防越权（详设 §3.4）。
        return sessions.stream()
                .filter(s -> s.getTenantId() != null && s.getTenantId().equals(current))
                .toList();
    }
}
