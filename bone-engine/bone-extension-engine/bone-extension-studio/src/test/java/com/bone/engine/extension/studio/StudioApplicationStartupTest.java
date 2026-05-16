package com.bone.engine.extension.studio;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = ExtensionStudioApplication.class)
@ActiveProfiles("test")
class StudioApplicationStartupTest {

    @Test
    void contextLoads() {}
}
