package com.bone.claim.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.bone.claim.adapter.web.dto.request.ClaimConfigRequests;
import com.bone.claim.adapter.web.dto.request.PolicyPageQry;
import com.bone.claim.application.ClaimConfigApplicationService;
import com.bone.claim.application.PolicyDetailApplicationService;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageParam;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 保单/计划/险种配置端点的契约钉子。
 *
 * <p><b>为什么用反射锁注解而不是只测 happy path</b>：路径漂成未登记前缀会整体 404 且后端无日志； 漏写 {@code @PreAuthorize} 会被 ArchUnit
 * 判红、或让端点对所有人 403。这两类都是「配了不生效」的形态， 必须在编译期之外再钉一次。返回类型锁 {@link ApiResponse} 对应 HC-003。
 */
class ClaimConfigCrudControllerTest {

  private static final List<Class<?>> CONTROLLERS =
      List.of(
          PolicyConfigController.class,
          PlanConfigController.class,
          CoverageConfigController.class,
          ClaimFlowConfigController.class);

  private static boolean isEndpoint(Method method) {
    return method.isAnnotationPresent(GetMapping.class)
        || method.isAnnotationPresent(PostMapping.class)
        || method.isAnnotationPresent(PutMapping.class)
        || method.isAnnotationPresent(DeleteMapping.class);
  }

  @Test
  @DisplayName("类级路径必须落在既有 /api/v1/claim-config 命名空间")
  void locksNamespace() {
    assertThat(PolicyConfigController.class.getAnnotation(RequestMapping.class).value())
        .containsExactly("/api/v1/claim-config/policies");
    assertThat(PlanConfigController.class.getAnnotation(RequestMapping.class).value())
        .containsExactly("/api/v1/claim-config/plans");
    assertThat(CoverageConfigController.class.getAnnotation(RequestMapping.class).value())
        .containsExactly("/api/v1/claim-config/coverages");
  }

  @Test
  @DisplayName("每个端点都带已有权限码且返回 ApiResponse（ArchUnit + HC-003）")
  void everyEndpointDeclaresAuthorityAndEnvelope() {
    for (Class<?> controller : CONTROLLERS) {
      for (Method method : controller.getDeclaredMethods()) {
        if (!isEndpoint(method)) {
          continue;
        }
        PreAuthorize authority = method.getAnnotation(PreAuthorize.class);
        assertThat(authority)
            .as("%s#%s 缺 @PreAuthorize", controller.getSimpleName(), method.getName())
            .isNotNull();
        assertThat(authority.value())
            .as("%s#%s 权限码", controller.getSimpleName(), method.getName())
            .contains("claim:policy:")
            .doesNotContain("claim:claims:");
        Class<?> returnType = method.getReturnType();
        if (returnType == ResponseEntity.class) {
          // 文件流端点按 Bone-API 规范返回 ResponseEntity<byte[]>，不套 ApiResponse 信封
          assertThat(method.getGenericReturnType().getTypeName())
              .as("%s#%s 文件流返回类型", controller.getSimpleName(), method.getName())
              .contains("ResponseEntity<byte[]>");
          assertThat(method.getName()).isEqualTo("export");
        } else {
          assertThat(returnType)
              .as("%s#%s 返回类型", controller.getSimpleName(), method.getName())
              .isEqualTo(ApiResponse.class);
        }
      }
    }
  }

  @Test
  @DisplayName("三个创建端点返回 201（配置面 E2E 契约）")
  void createEndpointsReturnCreated() throws NoSuchMethodException {
    assertCreated(
        PolicyConfigController.class.getMethod(
            "create", ClaimConfigRequests.CreatePolicyReq.class));
    assertCreated(
        PlanConfigController.class.getMethod("create", ClaimConfigRequests.CreatePlanReq.class));
    assertCreated(
        CoverageConfigController.class.getMethod(
            "create", ClaimConfigRequests.CreateCoverageReq.class));
  }

  private static void assertCreated(Method method) {
    ResponseStatus status = method.getAnnotation(ResponseStatus.class);
    assertThat(status).as("%s 缺 @ResponseStatus", method.getName()).isNotNull();
    assertThat(status.value()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  @DisplayName("集合根同时接受尾斜杠，避免 axios 拼出 /policies/ 404")
  void collectionRootAcceptsTrailingSlash() throws NoSuchMethodException {
    assertThat(
            PolicyConfigController.class
                .getMethod("list", PolicyPageQry.class)
                .getAnnotation(GetMapping.class)
                .value())
        .containsExactly("", "/");
    assertThat(
            PlanConfigController.class
                .getMethod("list", String.class)
                .getAnnotation(GetMapping.class)
                .value())
        .containsExactly("", "/");
    assertThat(
            CoverageConfigController.class
                .getMethod("list", long.class)
                .getAnnotation(GetMapping.class)
                .value())
        .containsExactly("", "/");
  }

  @Test
  @DisplayName("分页端点不得回退成裸 @RequestParam：入参必须继承 PageParam")
  void pagingEndpointUsesPageParamSubclass() throws NoSuchMethodException {
    Class<?>[] params =
        PolicyConfigController.class.getMethod("list", PolicyPageQry.class).getParameterTypes();
    assertThat(params).hasSize(1);
    assertThat(PageParam.class).isAssignableFrom(params[0]);
    assertThat(
            PolicyConfigController.class
                .getMethod("list", PolicyPageQry.class)
                .getParameters()[0]
                .getAnnotation(org.springframework.web.bind.annotation.ModelAttribute.class))
        .isNotNull();
  }

  @Test
  @DisplayName("POST /policies 的 Req→命令映射不丢字段：null 等待期转 0、null 保司号保持 null")
  void createPolicyMapsEveryField() {
    ClaimConfigApplicationService service = mock(ClaimConfigApplicationService.class);
    PolicyDetailApplicationService detail = mock(PolicyDetailApplicationService.class);
    Instant start = Instant.parse("2026-01-01T00:00:00Z");
    Instant end = Instant.parse("2026-12-31T00:00:00Z");
    ClaimConfigRequests.CreatePolicyReq request =
        new ClaimConfigRequests.CreatePolicyReq(
            "P-1",
            "测试保单",
            "保司",
            "分公司",
            "投保公司",
            "BIZ-1",
            "门诊",
            "GROUP",
            start,
            end,
            null,
            "v1",
            "[]",
            "NEED",
            "NOT_NEED",
            null);

    new PolicyConfigController(service, detail).create(request);

    ArgumentCaptor<ClaimConfigApplicationService.CreatePolicy> captor =
        ArgumentCaptor.forClass(ClaimConfigApplicationService.CreatePolicy.class);
    verify(service).createPolicy(captor.capture());
    ClaimConfigApplicationService.CreatePolicy command = captor.getValue();
    assertThat(command.policyNo()).isEqualTo("P-1");
    assertThat(command.bizIdentityCode()).isEqualTo("BIZ-1");
    assertThat(command.effectiveStart()).isEqualTo(start);
    assertThat(command.effectiveEnd()).isEqualTo(end);
    assertThat(command.waitingPeriod()).isZero();
    assertThat(command.insurerPolicyNo()).isNull();
    assertThat(command.docClaimNotice()).isEqualTo("NOT_NEED");
    verifyNoMoreInteractions(service, detail);
  }
}
