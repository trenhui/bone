package com.bone.masterdata.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.masterdata.application.command.RegisterSupplierCommand;
import com.bone.masterdata.application.command.TransitionSupplierCommand;
import com.bone.masterdata.application.command.TransitionVendorCommand;
import com.bone.masterdata.application.query.dto.SupplierRegistrationResultDTO;
import com.bone.masterdata.application.query.dto.SupplierStateDTO;
import com.bone.masterdata.application.query.dto.SupplierTransitionResultDTO;
import com.bone.masterdata.application.query.qry.SupplierPageQuery;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供应商治理端点的<b>静态契约</b>：不启 Spring 上下文，直接读注解与签名。
 *
 * <p>为什么要有这一层：HTTP 集成测试只能证明「当前这几条路径按预期工作」，漏掉「新增端点忘了打权限点」这类缺陷 —— 新端点没人测就没人发现。 静态契约把「所有写端点必须 write
 * 权限、所有端点必须被 ApiResponse 包装、不得裸返领域对象」变成对全类的量词断言， 新增端点会自动被纳入检查。
 */
class SupplierControllerContractTest {

  private static final String PATH = PlatformApiPaths.MASTERDATA_V1 + "/suppliers";

  @Test
  @DisplayName("类级契约：路径前缀 + 能力开关前缀与服务保持同一套")
  void classLevelContract() {
    RequestMapping mapping = SupplierController.class.getAnnotation(RequestMapping.class);
    assertThat(mapping).as("必须是 @RestController").isNotNull();
    assertThat(SupplierController.class.getAnnotation(RestController.class)).isNotNull();
    assertThat(mapping.value()).containsExactly(PATH);

    ConditionalOnProperty gate =
        SupplierController.class.getAnnotation(ConditionalOnProperty.class);
    assertThat(gate).as("端点必须受能力开关控制，不能无条件注册").isNotNull();
    assertThat(gate.prefix()).isEqualTo("bone.procurement.supplier-mdm");
    assertThat(gate.name()).containsExactly("enabled");
    assertThat(gate.havingValue()).isEqualTo("true");
  }

  @Test
  @DisplayName("权限契约：全部写端点 write、全部读端点 read，不允许有端点不带权限点")
  void everyHandlerDeclaresAuthority() {
    List<Method> handlers = handlers();
    // 反空跑守卫：下面的断言是对「全部端点」的量词，若 handlers() 因为注解改名而返回空列表，
    // 循环会零次执行、用例假绿。这里钉住当前端点数，新增/删除端点必须同步改这个数字。
    assertThat(handlers).as("端点数量变化时必须显式更新本用例，防止循环空跑").hasSize(5);

    for (Method handler : handlers) {
      PreAuthorize authority = handler.getAnnotation(PreAuthorize.class);
      assertThat(authority)
          .as("%s 缺 @PreAuthorize，等于任何持 token 的人都能调", handler.getName())
          .isNotNull();
      String expected =
          handler.isAnnotationPresent(PostMapping.class)
              ? "hasAuthority('masterdata:governance:write')"
              : "hasAuthority('masterdata:governance:read')";
      assertThat(authority.value()).as("%s 权限点不符", handler.getName()).isEqualTo(expected);
    }
  }

  @Test
  @DisplayName("响应契约：统一 ApiResponse 包装，列表额外用 PageResult 且不裸返领域对象")
  void everyHandlerReturnsWrappedPayload() {
    for (Method handler : handlers()) {
      assertThat(handler.getReturnType())
          .as("%s 必须返回 ApiResponse", handler.getName())
          .isEqualTo(ApiResponse.class);
      Type payload =
          ((ParameterizedType) handler.getGenericReturnType()).getActualTypeArguments()[0];
      String payloadName = payload.getTypeName();
      assertThat(payloadName)
          .as("%s 的响应体不得是领域对象（Supplier/Vendor/状态枚举）", handler.getName())
          .doesNotContain("domain.model");
    }

    assertThat(genericPayload(handler("register", RegisterSupplierCommand.class)).getTypeName())
        .isEqualTo(SupplierRegistrationResultDTO.class.getName());
    assertThat(
            genericPayload(
                    handler("transitionSupplier", String.class, TransitionSupplierCommand.class))
                .getTypeName())
        .isEqualTo(SupplierTransitionResultDTO.class.getName());
    assertThat(
            genericPayload(handler("transitionVendor", String.class, TransitionVendorCommand.class))
                .getTypeName())
        .isEqualTo(SupplierTransitionResultDTO.class.getName());
    assertThat(genericPayload(handler("current", String.class)).getTypeName())
        .isEqualTo(SupplierStateDTO.class.getName());

    // 列表：ApiResponse<PageResult<SupplierStateDTO>>，两层泛型都要核对，避免误改成裸 List。
    Type pagePayload = genericPayload(handler("page", SupplierPageQuery.class));
    assertThat(pagePayload).isInstanceOf(ParameterizedType.class);
    ParameterizedType pageType = (ParameterizedType) pagePayload;
    assertThat(pageType.getRawType()).isEqualTo(PageResult.class);
    assertThat(pageType.getActualTypeArguments()[0]).isEqualTo(SupplierStateDTO.class);
  }

  @Test
  @DisplayName("路由契约：路径与 @PathVariable 位置固定，partyId 只从路径取")
  void routeContract() {
    assertThat(
            handler("register", RegisterSupplierCommand.class)
                .getAnnotation(PostMapping.class)
                .value())
        .containsExactly("/registrations");
    assertThat(
            handler("transitionSupplier", String.class, TransitionSupplierCommand.class)
                .getAnnotation(PostMapping.class)
                .value())
        .containsExactly("/{partyId}/supplier-transitions");
    assertThat(
            handler("transitionVendor", String.class, TransitionVendorCommand.class)
                .getAnnotation(PostMapping.class)
                .value())
        .containsExactly("/{partyId}/vendor-transitions");
    assertThat(handler("current", String.class).getAnnotation(GetMapping.class).value())
        .containsExactly("/{partyId}");
    assertThat(handler("page", SupplierPageQuery.class).getAnnotation(GetMapping.class).value())
        .isEmpty();

    // 租户不接受入参自报：查询对象里不得出现 tenantId 字段。
    assertThat(SupplierPageQuery.class.getDeclaredFields())
        .as("分页查询不得携带 tenantId（租户取自登录态）")
        .noneMatch(f -> f.getName().equalsIgnoreCase("tenantId"));
  }

  // ---------------------------------------------------------------- helpers

  private static List<Method> handlers() {
    return Arrays.stream(SupplierController.class.getDeclaredMethods())
        .filter(
            m ->
                m.isAnnotationPresent(GetMapping.class) || m.isAnnotationPresent(PostMapping.class))
        .toList();
  }

  private static Method handler(String name, Class<?>... parameterTypes) {
    try {
      return SupplierController.class.getDeclaredMethod(name, parameterTypes);
    } catch (NoSuchMethodException e) {
      throw new AssertionError("端点方法不存在: " + name, e);
    }
  }

  private static Type genericPayload(Method handler) {
    return ((ParameterizedType) handler.getGenericReturnType()).getActualTypeArguments()[0];
  }
}
