package com.bone.metadata.engine.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bone.metadata.engine.adapter.po.MetaEntityPo;
import com.bone.metadata.engine.adapter.po.MetaFieldPo;
import com.bone.metadata.engine.metadata.EntityMetadata;
import com.bone.metadata.engine.spi.MetadataPlatformBridge;
import com.bone.metadata.sdk.Repository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link SdkMetadataRepository} 单元测试（适配器层）。
 *
 * <p>使用 Mockito mock SDK 的 {@code Repository<MetaEntityPo>} / {@code Repository<MetaFieldPo>}， 验证
 * Criteria 构造与 {@link MetaEntityConverter} 转换结果。<b>不依赖真实数据源</b> （引擎作为库无 SqlExecutor/数据源
 * bean，真实集成测试见 T6 server 侧）。
 */
@ExtendWith(MockitoExtension.class)
class SdkMetadataRepositoryTest {

  @Mock private Repository<MetaEntityPo, Long> entityRepository;
  @Mock private Repository<MetaFieldPo, Long> fieldRepository;
  @Mock private MetadataPlatformBridge platformBridge;

  private SdkMetadataRepository repository;

  @BeforeEach
  void setUp() {
    // 手动构造：规避 @InjectMocks 对两个泛型擦除相同的 Repository 参数按类型注入歧义。
    repository = new SdkMetadataRepository(entityRepository, fieldRepository, platformBridge);
  }

  private MetaEntityPo sampleEntity() {
    MetaEntityPo po = new MetaEntityPo();
    po.setId(1L);
    po.setName("订单");
    po.setCode("order");
    po.setDisplayName("订单");
    po.setDescription("订单实体");
    po.setTableName("t_order");
    po.setStatus(2);
    po.setBuiltin(false);
    po.setTenantId(100L);
    return po;
  }

  private MetaFieldPo sampleField() {
    MetaFieldPo fp = new MetaFieldPo();
    fp.setId(10L);
    fp.setEntityId(1L);
    fp.setName("order_no");
    fp.setCode("orderNo");
    fp.setDisplayName("订单号");
    fp.setType("string");
    fp.setRequired(true);
    fp.setUnique(true);
    fp.setPk(true);
    fp.setIndexed(true);
    fp.setLength(64);
    fp.setTenantId(100L);
    return fp;
  }

  @Test
  void findEntityByApiName_shouldConvertPoToEntityMetadata() {
    when(platformBridge.currentTenantId()).thenReturn(Optional.of("100"));
    when(entityRepository.findOneByCriteria(org.mockito.ArgumentMatchers.any()))
        .thenReturn(sampleEntity());
    when(fieldRepository.findByCriteria(org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(sampleField()));

    EntityMetadata result = repository.findEntityByApiName("order");

    assertThat(result).isNotNull();
    assertThat(result.getApiName()).isEqualTo("order");
    assertThat(result.getName()).isEqualTo("订单");
    assertThat(result.getTableName()).isEqualTo("t_order");
    assertThat(result.getFields()).containsKey("orderNo");
    assertThat(result.getFields().get("orderNo").isPrimaryKey()).isTrue();
    assertThat(result.getFields().get("orderNo").getType()).isEqualTo("string");
  }

  @Test
  void findEntityById_emptyId_shouldReturnEmpty() {
    assertThat(repository.findEntityById("")).isEmpty();
    assertThat(repository.findEntityById(null)).isEmpty();
  }

  @Test
  void findAllEntities_shouldReturnConvertedList() {
    when(platformBridge.currentTenantId()).thenReturn(Optional.of("100"));
    when(entityRepository.findByCriteria(org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(sampleEntity()));
    when(fieldRepository.findByCriteria(org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(sampleField()));

    List<EntityMetadata> all = repository.findAllEntities();

    assertThat(all).hasSize(1);
    assertThat(all.get(0).getApiName()).isEqualTo("order");
  }

  @Test
  void findAllDomains_shouldReturnEmpty_whenNoDomainColumn() {
    assertThat(repository.findAllDomains()).isEmpty();
  }
}
