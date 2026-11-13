package com.bone.masterdata.domain.model.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.exception.DomainException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** xref 领域服务纯单测：重点是「同键改绑」与「解析多值」两条 fail-closed 约束。 */
class EntityKeyMappingRegistryTest {

  private static final long TENANT = 7L;
  private final EntityKeyMappingRegistry registry = new EntityKeyMappingRegistry();

  private static EntityKeyMapping mapping(long tenant, String party, String system, String local) {
    return EntityKeyMapping.active(tenant, party, system, local);
  }

  @Test
  void registersNewMapping() {
    EntityKeyMapping candidate = mapping(TENANT, "P-1", "ERP", "V001");
    assertEquals(candidate, registry.register(List.of(), candidate));
  }

  @Test
  void registeringSameKeyAndPartyIsIdempotent() {
    EntityKeyMapping existing = mapping(TENANT, "P-1", "ERP", "V001");
    EntityKeyMapping duplicate = mapping(TENANT, "P-1", "ERP", "V001");
    assertSame(existing, registry.register(List.of(existing), duplicate));
  }

  @Test
  void rebindingLocalKeyToAnotherPartyFailsClosed() {
    EntityKeyMapping existing = mapping(TENANT, "P-1", "ERP", "V001");
    EntityKeyMapping conflicting = mapping(TENANT, "P-2", "ERP", "V001");

    DomainException ex =
        assertThrows(
            DomainException.class, () -> registry.register(List.of(existing), conflicting));
    assertTrue(ex.getMessage().contains("禁止自动改绑"));
  }

  @Test
  void sameLocalKeyInDifferentTenantsDoesNotConflict() {
    EntityKeyMapping other = mapping(8L, "P-9", "ERP", "V001");
    EntityKeyMapping mine = mapping(TENANT, "P-1", "ERP", "V001");
    assertEquals(mine, registry.register(List.of(other), mine));
  }

  @Test
  void resolvesActiveMappingOnlyForOwningTenant() {
    List<EntityKeyMapping> mappings =
        List.of(mapping(TENANT, "P-1", "ERP", "V001"), mapping(8L, "P-9", "ERP", "V001"));

    assertEquals(Optional.of("P-1"), registry.resolve(TENANT, "ERP", "V001", mappings));
    assertEquals(Optional.of("P-9"), registry.resolve(8L, "ERP", "V001", mappings));
    assertTrue(registry.resolve(99L, "ERP", "V001", mappings).isEmpty());
  }

  @Test
  void resolvingPolutedKeyWithTwoPartiesFailsClosed() {
    List<EntityKeyMapping> mappings =
        List.of(mapping(TENANT, "P-1", "ERP", "V001"), mapping(TENANT, "P-2", "ERP", "V001"));

    DomainException ex =
        assertThrows(
            DomainException.class, () -> registry.resolve(TENANT, "ERP", "V001", mappings));
    assertTrue(ex.getMessage().contains("多个有效 partyId"));
  }

  @Test
  void onePartyMayHaveMultipleLocalIdsPerSystem() {
    List<EntityKeyMapping> mappings =
        List.of(
            mapping(TENANT, "P-1", "ERP", "V001"),
            mapping(TENANT, "P-1", "ERP", "V002"),
            mapping(TENANT, "P-2", "ERP", "V003"));

    assertEquals(2, registry.localIdsOf(TENANT, "P-1", "ERP", mappings).size());
  }

  @Test
  void invalidConfidenceIsRejected() {
    assertThrows(
        DomainException.class, () -> EntityKeyMapping.of(TENANT, "P-1", "ERP", "V001", 1.5, true));
  }
}
