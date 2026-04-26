package com.bone.lowcode.infra;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = com.bone.lowcode.infra.PageConfigApplication.class,
        properties = { "env=DEV","apollo.meta=http://192.168.8.136:8096"
                ,
                "file.encoding=UTF-8"
        })
@Transactional
@ActiveProfiles("local")
@Rollback
public class BaseTest {


}
