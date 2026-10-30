package com.bone.metadata.catalog.adapter.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bone.core.exception.DomainException;
import com.bone.metadata.catalog.application.UiOverlayApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@code PUT /{entityCode}/ui-overlay} 的空载荷契约测试。
 *
 * <p><b>真实缺陷</b>：本端点是 UPSERT <b>全量替换</b>语义，但此前对载荷不做任何校验。 客户端发 {@code {}} 时， {@code
 * "{}\n".toString()} 得到字符串 {@code "{}"}——它既非 null 也非空白， 能顺利绕过适配器的空值守卫直接落库 ⇒
 * <b>用户已保存的列偏好被静默抹掉</b>，而接口仍返回 200「操作成功」（谎报成功 + 数据丢失）。 实测三步：先存 {@code {"cols":["a","b"]}} → 回读正确；再
 * PUT {@code {}} → 200；回读变成 {@code {}}。
 *
 * <p><b>清空有专属出口</b>：{@code DELETE /{entityCode}/ui-overlay}（前端 {@code resetOverlay} 走它）， 前端 {@code
 * persistOverlay} 恒传完整 bundle。故 PUT 空对象<b>没有任何合法语义</b>，拒绝它零回归风险。
 *
 * <p>不启动 Spring 上下文（与本模块其它契约测试同风格）：直接 new 控制器 + mock 依赖， 断言「拒绝 + 绝不落库」两件事。
 */
@ExtendWith(MockitoExtension.class)
class UiOverlayEmptyPayloadTest {

  @Mock private UiOverlayApplicationService uiOverlayApplicationService;

  @InjectMocks private MetaEntityCatalogController controller;

  private static final String ENTITY = "demo_entity";

  @Test
  @DisplayName("空对象 {} → 拒绝，且绝不落库（否则会静默抹掉用户已保存的列偏好）")
  void emptyObjectIsRejectedAndNeverPersisted() {
    ObjectNode empty = new ObjectMapper().createObjectNode();

    assertThrows(
        DomainException.class,
        () -> controller.saveUiOverlay(ENTITY, empty),
        "PUT 空对象必须被拒——它是全量替换，会把已存覆盖抹成 {}");

    verify(uiOverlayApplicationService, never()).saveOverlayJson(anyString(), anyString());
  }

  @Test
  @DisplayName("null 载荷 → 拒绝，且绝不落库")
  void nullPayloadIsRejected() {
    assertThrows(DomainException.class, () -> controller.saveUiOverlay(ENTITY, null));
    verify(uiOverlayApplicationService, never()).saveOverlayJson(anyString(), anyString());
  }

  @Test
  @DisplayName("非对象载荷（数组/标量）→ 拒绝")
  void nonObjectPayloadIsRejected() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    assertThrows(
        DomainException.class,
        () -> controller.saveUiOverlay(ENTITY, mapper.readTree("[\"a\"]")),
        "数组不是合法的列投影覆盖结构");
    assertThrows(
        DomainException.class,
        () -> controller.saveUiOverlay(ENTITY, mapper.readTree("\"plain-string\"")),
        "标量不是合法的列投影覆盖结构");
    verify(uiOverlayApplicationService, never()).saveOverlayJson(anyString(), anyString());
  }

  @Test
  @DisplayName("非空对象 → 正常落库（正常路径未被新校验误伤）")
  void nonEmptyObjectIsPersisted() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode payload = (ObjectNode) mapper.readTree("{\"cols\":[\"a\",\"b\"]}");

    controller.saveUiOverlay(ENTITY, payload);

    verify(uiOverlayApplicationService)
        .saveOverlayJson(eq(ENTITY), eq(mapper.writeValueAsString(payload)));
  }

  @Test
  @DisplayName("拒绝信息必须指向正确的清空出口，避免调用方误以为接口坏了")
  void rejectionMessagePointsToDeleteEndpoint() {
    ObjectNode empty = new ObjectMapper().createObjectNode();
    DomainException ex =
        assertThrows(DomainException.class, () -> controller.saveUiOverlay(ENTITY, empty));
    assertEquals(
        "界面列投影覆盖不能为空对象；如需恢复默认请调用 DELETE /ui-overlay",
        ex.getMessage(),
        "错误信息要把调用方引导到正确的清空出口（DELETE），而不是让它以为 PUT 坏了");
  }

  /** 独立的清空出口必须仍然可用——它与 PUT 是两条路径，补校验不得波及。 */
  @Test
  @DisplayName("清空仍走 DELETE 出口，不受 PUT 校验影响")
  void clearEndpointStillWorks() {
    MetaEntityCatalogController fresh =
        new MetaEntityCatalogController(
            null, uiOverlayApplicationService, null, null, new ObjectMapper());

    fresh.clearUiOverlay(ENTITY);

    verify(uiOverlayApplicationService).clearOverlay(ENTITY);
  }
}
