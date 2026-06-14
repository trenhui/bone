package com.bone.engine.extension.studio.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.bone.engine.extension.studio.domain.gateway.ExtensionReadPort;
import com.bone.engine.extension.studio.domain.model.ExtPoint;
import com.bone.engine.extension.studio.domain.model.Extension;
import com.bone.engine.extension.studio.domain.repository.ExtPointRepository;
import com.bone.engine.extension.studio.domain.repository.ExtensionRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** metadata + MySQL（Testcontainers）集成；DDL 与 {@code bone-init.sql} exts_* 对齐。 */
@Tag("docker")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    classes = com.bone.engine.extension.studio.support.MetadataPersistenceTestApplication.class)
@ActiveProfiles({"test", "mysql-it"})
class StudioMetadataMysqlIT {

  @Container
  @SuppressWarnings("resource")
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>("mysql:8.0.33")
          .withDatabaseName("bone")
          .withUsername("test")
          .withPassword("test")
          .withInitScript("db/exts-mysql-test.sql");

  @DynamicPropertySource
  static void mysqlProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
  }

  @Autowired private ExtPointRepository extPointRepository;

  @Autowired private ExtensionRepository extensionRepository;

  @Autowired private ExtensionReadPort extensionReadPort;

  @Test
  void saveExtPointAndExtensionAgainstMysql() {
    ExtPoint point = new ExtPoint();
    point.setName("MySQL IT 扩展点");
    point.setInterfaceName("com.bone.test.MysqlItExtPoint");
    point.setDomain("test");
    point.setEnabled(true);
    extPointRepository.save(point);
    assertNotNull(point.getId());

    Extension extension =
        Extension.create(point.getId(), "MySQL 实现", "mysql it", "com.bone.test.MysqlItImpl");
    extensionRepository.save(extension);
    assertNotNull(extension.getId());

    ExtPoint loaded = extPointRepository.findByInterfaceName("com.bone.test.MysqlItExtPoint");
    assertNotNull(loaded);
    assertEquals("MySQL IT 扩展点", loaded.getName());
    assertEquals(1, extensionReadPort.findByExtPointId(point.getId()).size());
  }
}
