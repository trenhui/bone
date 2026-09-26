package com.bone.engine.extension.studio.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.engine.extension.studio.ExtensionStudioApplication;
import com.bone.engine.extension.studio.domain.gateway.PluginVersionReadPort;
import com.bone.engine.extension.studio.domain.model.extension.Extension;
import com.bone.engine.extension.studio.domain.model.extpoint.ExtPoint;
import com.bone.engine.extension.studio.domain.model.plugin.DeploymentStatus;
import com.bone.engine.extension.studio.domain.model.plugin.PluginVersion;
import com.bone.engine.extension.studio.domain.repository.ExtPointRepository;
import com.bone.engine.extension.studio.domain.repository.ExtensionRepository;
import com.bone.engine.extension.studio.domain.repository.PluginVersionRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * 5a G3 生命周期分权：deploy-publishes=false 时 :deploy 停在 STAGED，生效由 :publish-runtime 切 ACTIVE。 （默认
 * deployPublishes=true 的 As-Is 行为由 ExtensionApiContractTest deployAction_colonSuffix 覆盖。）
 */
@SpringBootTest(classes = ExtensionStudioApplication.class)
@ActiveProfiles("in-memory")
@TestPropertySource(properties = "bone.extension.studio.lifecycle.deploy-publishes=false")
class DeployStagedLifecycleTest {

  @Autowired private ExtensionCommandApplicationService commandService;
  @Autowired private ExtensionRepository extensionRepository;
  @Autowired private PluginVersionRepository pluginVersionRepository;
  @Autowired private PluginVersionReadPort pluginVersionReadPort;
  @Autowired private ExtPointRepository extPointRepository;

  @Test
  void deployStaysStagedAndPublishRuntimeActivates() {
    ExtPoint point = new ExtPoint();
    point.setName("分权测试扩展点");
    point.setInterfaceName("com.bone.e2e.GuardedExt_" + System.nanoTime());
    point = extPointRepository.save(point);

    Extension plugin =
        Extension.create(point.getId(), "分权测试插件", "5a G3", "com.bone.e2e.impl.GuardedImpl");
    plugin = extensionRepository.save(plugin);

    PluginVersion version = new PluginVersion();
    version.setPluginId(plugin.getId());
    version.setVersion("1.0.0");
    version.setChecksum("test-checksum");
    version.setActive(true);
    version.setDeploymentStatus(DeploymentStatus.STAGED);
    version.setCreatedAt(LocalDateTime.now());
    pluginVersionRepository.save(version);

    boolean deployed = commandService.deployExtension(plugin.getId());
    assertTrue(deployed);

    Extension deployedPlugin = extensionRepository.findById(plugin.getId());
    assertEquals(true, deployedPlugin.isEnabled(), "部署后元数据应为启用态");
    assertEquals(
        DeploymentStatus.STAGED.name(),
        activeVersionStatus(plugin.getId()),
        "分权模式下 :deploy 不切 ACTIVE（部署 ≠ 生效）");

    boolean published = commandService.publishRuntime(plugin.getId());
    assertTrue(published);
    assertEquals(
        DeploymentStatus.ACTIVE.name(),
        activeVersionStatus(plugin.getId()),
        ":publish-runtime 切 ACTIVE（生效切换点）");
  }

  private String activeVersionStatus(Long pluginId) {
    return pluginVersionReadPort.findByPluginId(pluginId).stream()
        .filter(PluginVersion::isActive)
        .findFirst()
        .map(PluginVersion::getDeploymentStatus)
        .orElse(null);
  }
}
