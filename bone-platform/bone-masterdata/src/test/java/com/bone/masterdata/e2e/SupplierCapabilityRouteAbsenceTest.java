package com.bone.masterdata.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.core.security.jwt.JwtTokenService;
import com.bone.masterdata.adapter.web.controller.SupplierController;
import com.bone.masterdata.testsupport.MetadataSdkIntegrationTestConfiguration;
import com.bone.masterdata.testsupport.supplier.SupplierHttpTestConfiguration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * 能力开关关闭态的<b>真路由</b>探针：带合法 JWT、带正确权限点请求治理路径，必须得到 404，而不是 200/401/403/500。
 *
 * <p>为什么不能只断言「Controller bean 不存在」：bean 不在不等于路由不在 —— 只要有人把 {@code @RequestMapping} 挪到别的类、 或给端点加个静态
 * {@code @GetMapping} 壳子，bean 断言仍绿而路由已经暴露。这里走真实 servlet 栈，把「没注册」验证成 HTTP 事实。
 *
 * <p>同时断言 401 不在预期内：401 说明请求被安全链拦下而不是「路由不存在」，会掩盖真实状态；因此 token 与权限点都必须是有效的。
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "bone.procurement.supplier-mdm.enabled=false")
@ActiveProfiles("test")
@Import({MetadataSdkIntegrationTestConfiguration.class, SupplierHttpTestConfiguration.class})
class SupplierCapabilityRouteAbsenceTest {

  private static final String BASE = "/api/v1/masterdata/suppliers";

  @Autowired private TestRestTemplate rest;
  @Autowired private JwtTokenService jwtTokenService;
  @Autowired private ApplicationContext context;

  @Test
  @DisplayName("开关关闭时治理路由全部不存在（404），且 Controller 未装配")
  void disabledCapabilityShouldNotExposeRoutes() {
    assertThat(context.getBeanNamesForType(SupplierController.class)).isEmpty();

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(
        jwtTokenService.generateToken(
            1L,
            "procurement-e2e-tester",
            1L,
            List.of("masterdata:governance:write", "masterdata:governance:read")));
    headers.add("X-Tenant-Id", "1");

    assertThat(status(HttpMethod.GET, BASE + "/SUP-1", headers)).isEqualTo(404);
    assertThat(status(HttpMethod.GET, BASE + "?page=1&size=10", headers)).isEqualTo(404);
    assertThat(status(HttpMethod.POST, BASE + "/registrations", headers)).isEqualTo(404);
    assertThat(status(HttpMethod.POST, BASE + "/SUP-1/supplier-transitions", headers))
        .isEqualTo(404);
    assertThat(status(HttpMethod.POST, BASE + "/SUP-1/vendor-transitions", headers)).isEqualTo(404);
  }

  private int status(HttpMethod method, String path, HttpHeaders headers) {
    ResponseEntity<String> resp =
        rest.exchange(path, method, new HttpEntity<>(headers), String.class);
    return resp.getStatusCode().value();
  }
}
