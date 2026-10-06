package com.bone.iam.adapter.web.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * IAM 高危写端点的**端到端**授权断言（2026-10-05，设计诊断 P1-9 的剩余缺口）。
 *
 * <p>本模块此前的授权测试只到「方法拦截器求值」这一层（见 {@code IamWriteEndpointAuthorizationTest}），
 * 再往前还有两道只在真实过滤器链里才发生的事：{@code SecurityConfig} 的认证强制，以及 {@code AccessDeniedHandler} 把拒绝翻译成
 * 403。这两道任一失效，方法级断言照样全绿。
 *
 * <p><b>已登录但零权限码 ⇒ 403</b>：{@code @WithMockUser(authorities = {})} 刻意不给任何权限码 （连默认的 {@code
 * ROLE_USER} 都覆盖掉），模拟「普通登录用户」——这正是攻击者的真实起点。
 *
 * <p><b>必须带 {@code csrf()}</b>：Spring Security 6 默认开启 CSRF，POST/PUT/DELETE 缺 CSRF token 会先被 CSRF 拦成
 * 403 —— 那样断言虽然「绿」，但绿的原因与授权毫无关系，属典型假绿。故对照组断言 「带权限码时不得为 403」，用它证明 403 确实来自授权判定。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IamHighRiskEndpointHttpAuthorizationTest {

  private static final String TENANTS = "/api/v1/iam/tenants";
  private static final String ACCOUNTS = "/api/v1/iam/accounts";
  private static final String ROLES = "/api/v1/iam/roles";
  private static final String SESSIONS = "/api/v1/iam/sessions";

  @Autowired private MockMvc mockMvc;

  // ---------------------------------------------------------------- 负向：已登录、无权限码 ⇒ 403

  @Test
  @WithMockUser(authorities = {})
  @DisplayName("创建租户：普通登录用户被 403")
  void createTenantDeniedForPlainUser() throws Exception {
    mockMvc
        .perform(post(TENANTS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = {})
  @DisplayName("重置他人账号密码：普通登录用户被 403")
  void resetPasswordDeniedForPlainUser() throws Exception {
    mockMvc
        .perform(
            post(ACCOUNTS + "/1/reset-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = {})
  @DisplayName("吊销全局会话：普通登录用户被 403")
  void revokeSessionDeniedForPlainUser() throws Exception {
    mockMvc.perform(delete(SESSIONS + "/1").with(csrf())).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = {})
  @DisplayName("给角色授予权限：普通登录用户被 403")
  void assignRolePermissionsDeniedForPlainUser() throws Exception {
    mockMvc
        .perform(
            post(ROLES + "/1/permissions")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = {})
  @DisplayName("修改租户（含配额）：普通登录用户被 403")
  void updateTenantDeniedForPlainUser() throws Exception {
    mockMvc
        .perform(
            put(TENANTS + "/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------- 认证强制：未登录 ⇒ 401

  @Test
  @DisplayName("未认证请求 ⇒ 401（认证强制与授权是两件事，两者都要在 HTTP 层立住）")
  void anonymousIsUnauthorizedNotForbidden() throws Exception {
    mockMvc
        .perform(post(TENANTS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------- 对照组：403 必须来自授权

  @Test
  @WithMockUser(authorities = "iam:roles:read")
  @DisplayName("对照组：带权限码时不得为 403（证明上面的 403 来自授权而非 CSRF）")
  void grantedRequestIsNotForbidden() throws Exception {
    mockMvc
        .perform(get(ROLES + "/1").with(csrf()))
        .andExpect(
            result ->
                org.junit.jupiter.api.Assertions.assertNotEquals(
                    403,
                    result.getResponse().getStatus(),
                    "带权限码仍被 403 ⇒ 403 来自 CSRF 或其他非授权环节，本组断言是假绿"));
  }
}
