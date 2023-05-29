package com.bone.base.datapermission.core.utils;

import com.bone.base.datapermission.core.aop.DataPermissionContextHolder;
import com.bone.base.datapermission.core.util.DataPermissionUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DataPermissionUtilsTest {

    @Test
    public void testExecuteIgnore() {
        DataPermissionUtils.executeIgnore(() -> Assertions.assertFalse(DataPermissionContextHolder.get().enable()));
    }

}
