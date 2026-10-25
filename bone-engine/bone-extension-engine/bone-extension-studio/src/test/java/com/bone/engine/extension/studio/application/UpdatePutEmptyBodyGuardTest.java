package com.bone.engine.extension.studio.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.engine.extension.studio.ExtensionStudioApplication;
import com.bone.engine.extension.studio.domain.model.extension.Extension;
import com.bone.engine.extension.studio.domain.model.extpoint.ExtPoint;
import com.bone.engine.extension.studio.domain.repository.ExtPointRepository;
import com.bone.engine.extension.studio.domain.repository.ExtensionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * PUT 全量替换端点的空体守卫回归。
 *
 * <p>背景：{@code updatePoint}/{@code updatePlugin} 曾无必填校验，空 JSON 体会把 name/interfaceName/className
 * 等整列抹成 null。对扩展点还有第二重后果：{@code ExtPoint.enabled} 是 primitive boolean 且无字段初值， 缺省反序列化为 {@code false}
 * ⇒ 静默禁用；而 {@code Extension.enabled} 有初值 {@code true}，插件不涉及禁用。 该缺陷在写端点空体扫描中表现为「2xx 成功」而非
 * 5xx，故极易被漏过。
 *
 * <p>判据为「性质」而非「结果」：断言①拒绝为 400 ②落库实体未被改写（version 不变、字段原值保留）。 若删掉实现里的守卫，②会因底层真的写库而转红——不是空转的绿灯。
 */
@SpringBootTest(classes = ExtensionStudioApplication.class)
@ActiveProfiles("in-memory")
class UpdatePutEmptyBodyGuardTest {

  @Autowired private ExtPointCommandApplicationService extPointCommandApplicationService;
  @Autowired private ExtensionStudioApplicationService extensionStudioApplicationService;
  @Autowired private ExtPointRepository extPointRepository;
  @Autowired private ExtensionRepository extensionRepository;

  @Test
  @DisplayName("PUT /points/{id} 空体必须 400，且不得抹掉已存扩展点")
  void updatePointWithEmptyBodyIsRejectedAndPreservesStoredEntity() {
    ExtPoint point = new ExtPoint();
    point.setName("空体守卫扩展点");
    point.setInterfaceName("com.bone.e2e.PutGuard_" + System.nanoTime());
    point.setEnabled(true);
    point = extPointRepository.save(point);
    Long id = point.getId();
    int versionBefore = point.getVersion();

    ResponseEntity<com.bone.core.model.ApiResponse<ExtPoint>> response =
        extPointCommandApplicationService.updatePoint(id, new ExtPoint(), null);

    assertEquals(400, response.getStatusCode().value(), "空体 PUT 必须拒绝为 400，不得写库");
    assertFalse(response.getBody().isSuccess(), "拒绝响应的 success 必须为 false");

    ExtPoint stored = extPointRepository.findById(id);
    assertNotNull(stored, "扩展点不得被删除");
    assertEquals("空体守卫扩展点", stored.getName(), "name 不得被抹成 null");
    assertEquals(versionBefore, stored.getVersion(), "version 不得自增：证明守卫真的拦住了写库");
    assertTrue(stored.isEnabled(), "enabled 不得被反序列化缺省值翻成 false");
  }

  @Test
  @DisplayName("PUT /plugins/{id} 空体必须 400，且不得抹掉已存插件")
  void updatePluginWithEmptyBodyIsRejectedAndPreservesStoredEntity() {
    ExtPoint point = new ExtPoint();
    point.setName("插件空体守卫扩展点");
    point.setInterfaceName("com.bone.e2e.PutGuardPoint_" + System.nanoTime());
    point.setEnabled(true);
    point = extPointRepository.save(point);

    Extension plugin =
        Extension.create(point.getId(), "空体守卫插件", "1.0.0", "com.bone.e2e.PutGuardImpl");
    plugin = extensionRepository.save(plugin);
    Long id = plugin.getId();
    int versionBefore = plugin.getVersion();

    // Extension 无参构造是 private，故用工厂造「全字段缺省」实例，等价模拟 Jackson 解析 {} 的产物。
    Extension emptyBody = Extension.create(null, null, null, null);
    ResponseEntity<com.bone.core.model.ApiResponse<Extension>> response =
        extensionStudioApplicationService.updatePlugin(id, emptyBody, null);

    assertEquals(400, response.getStatusCode().value(), "空体 PUT 必须拒绝为 400，不得写库");
    assertFalse(response.getBody().isSuccess(), "拒绝响应的 success 必须为 false");

    Extension stored = extensionRepository.findById(id);
    assertNotNull(stored, "插件不得被删除");
    assertEquals("空体守卫插件", stored.getName(), "name 不得被抹成 null");
    assertEquals(versionBefore, stored.getVersion(), "version 不得自增：证明守卫真的拦住了写库");
  }
}
