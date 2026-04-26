package com.bone.tpa.claim.util;

import com.bone.tpa.claim.application.enums.BizModelEnum;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class BizFieldUtil {


    /**
     * 根据业务模型，从对应map中取出该模型的专属字段列表
     *
     * @param extraProperties   专属字段集合
     * @param bizFieldEnum      业务模型枚举
     */
    public static Map<String, Object> fetchExtraBizField(Map<String, Object> extraProperties, Map<String, List<String>> bizFieldMap, BizModelEnum bizFieldEnum) {
        Map<String, Object> extraBizFieldMap = new HashMap<>();

        if (extraProperties == null || extraProperties.isEmpty()) {
            return extraBizFieldMap;
        }

        log.info("获取专属字段: " + bizFieldMap);

        List<String> claimDetailBizField = bizFieldMap.get(bizFieldEnum.getCode());

        if (claimDetailBizField == null || claimDetailBizField.isEmpty()) {
            return extraBizFieldMap;
        }

        for (String key : extraProperties.keySet()) {
            if (claimDetailBizField.contains(key)) {
                extraBizFieldMap.put(key, extraProperties.get(key));
            }
        }

        return extraBizFieldMap;
    }
}
