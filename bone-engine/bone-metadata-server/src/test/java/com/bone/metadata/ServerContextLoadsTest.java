package com.bone.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.metadata.engine.ports.spi.MetadataRepositoryPort;
import com.bone.metadata.engine.runtime.adapter.po.MetaEntityPo;
import com.bone.metadata.engine.runtime.adapter.po.MetaFieldPo;
import com.bone.metadata.sdk.Repository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * P4.2 / P5.3 运行时装配与读路径集成测试。
 *
 * <p>验证目标：
 *
 * <ul>
 *   <li>P4.2：server 完整上下文可启动，engine 经 {@code @EnableSqlRepositories} 生成的 {@code
 *       Repository<MetaEntityPo, Long>} / {@code Repository<MetaFieldPo, Long>} bean 存在。
 *   <li>P5.3：{@code MetadataRepositoryPort}（SdkMetadataRepository）经真实 MySQL 数据源读取已发布实体， 装配 + SQL 通路
 *       + 转换器（MetaEntityConverter）全链路正确。
 * </ul>
 *
 * <p>依赖真实 MySQL（profile=local，jdbc:mysql://localhost:3306/bone）。测试环境未设置 TenantContext， 故 {@code
 * SdkMetadataRepository} 不加租户过滤，读取 meta_entity 全量行（当前库含 30 行已发布实体）。
 */
@SpringBootTest
class ServerContextLoadsTest {

  @Autowired(required = false)
  private Repository<MetaEntityPo, Long> entityRepository;

  @Autowired(required = false)
  private Repository<MetaFieldPo, Long> fieldRepository;

  @Autowired(required = false)
  private MetadataRepositoryPort metadataRepositoryPort;

  @Test
  void serverContext_shouldLoad_and_wireEngineRepositories() {
    // P4.2：engine PO 的 Repository bean 由 @EnableSqlRepositories 提供，且 MetadataRepositoryPort 可注入
    assertThat(entityRepository).isNotNull();
    assertThat(fieldRepository).isNotNull();
    assertThat(metadataRepositoryPort).isNotNull();
  }

  @Test
  void sdkMetadataRepository_shouldReadPublishedEntities_fromRealDataSource() {
    // P5.3：经 engine 读已发布实体——装配 + SQL 通路 + 转换器全链路
    assertThat(metadataRepositoryPort).isNotNull();
    List<?> allEntities = metadataRepositoryPort.findAllEntities();
    assertThat(allEntities).isNotNull().isNotEmpty();
    // 真实库含 meta_entity 全量行（当前 30 行），验证能读到真实数据
    assertThat(allEntities.size()).isGreaterThan(0);
  }
}
