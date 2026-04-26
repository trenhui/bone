package com.bone.tpa.push.feign.response;

import lombok.Data;

import java.util.List;

/**
 * @Author feihaiming
 *
 * @create 2025/9/18 18:18
 */
@Data
public class SystemDictResponse {
    private List<SysDictDTO> sysDicts;
}
