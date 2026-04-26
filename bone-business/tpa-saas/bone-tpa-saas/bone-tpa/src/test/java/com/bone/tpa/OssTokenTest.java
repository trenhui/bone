package com.bone.tpa;

import com.alibaba.fastjson.JSONObject;
import com.bone.tpa.claim.adapter.BasicConfigController;
import com.bone.tpa.test.BaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class OssTokenTest extends BaseTest {
    @Autowired
    BasicConfigController basicConfigController;

    @Test
    public void test() {
        System.out.println(JSONObject.toJSONString(basicConfigController.getOssConfig()));
    }

}
