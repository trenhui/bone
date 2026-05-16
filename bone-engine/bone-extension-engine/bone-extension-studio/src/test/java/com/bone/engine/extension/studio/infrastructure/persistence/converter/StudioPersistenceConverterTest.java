package com.bone.engine.extension.studio.infrastructure.persistence.converter;

import com.bone.engine.extension.studio.domain.model.ExtPoint;
import com.bone.engine.extension.studio.domain.model.Extension;
import com.bone.engine.extension.studio.infrastructure.persistence.entity.ExtStudioExtensionImpl;
import com.bone.engine.extension.studio.infrastructure.persistence.entity.ExtStudioExtensionPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudioPersistenceConverterTest {

    @Test
    void roundTripExtPoint() {
        ExtPoint domain = new ExtPoint();
        domain.setId(1L);
        domain.setName("支付扩展点");
        domain.setInterfaceName("com.bone.PaymentExtPoint");
        domain.setDomain("payment");
        domain.setEnabled(true);

        ExtStudioExtensionPoint row = StudioPersistenceConverter.toEntity(domain);
        assertEquals("com.bone.PaymentExtPoint", row.getPointCode());

        ExtPoint back = StudioPersistenceConverter.toDomain(row);
        assertEquals(domain.getName(), back.getName());
        assertTrue(back.isEnabled());
    }

    @Test
    void roundTripExtensionWithConfig() {
        Extension domain =
                Extension.create(10L, "VIP", "desc", "com.bone.VipExtension");
        domain.setId(2L);
        domain.setConfig("{\"traffic\":50,\"defaultImpl\":true}");
        domain.setPriority(10);

        ExtStudioExtensionImpl row = StudioPersistenceConverter.toEntity(domain);
        assertEquals(50, row.getRolloutPercent());
        assertTrue(row.getIsDefault());

        Extension back = StudioPersistenceConverter.toDomain(row);
        assertEquals("VIP", back.getName());
        assertEquals(10, back.getPriority());
    }
}
