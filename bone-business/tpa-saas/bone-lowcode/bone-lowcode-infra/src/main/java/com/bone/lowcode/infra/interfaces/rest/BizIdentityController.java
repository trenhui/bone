package com.bone.lowcode.infra.interfaces.rest;

import com.bone.core.result.Result;
import com.bone.lowcode.infra.application.dto.bizIdentity.ListBizIdentityDTO;
import com.bone.lowcode.infra.application.service.BizIdentityApplicationService;
import com.bone.lowcode.infra.application.vo.PageResult;
import com.bone.lowcode.infra.application.vo.bizIdentity.GetAllBizIdentityVo;
import com.bone.lowcode.infra.application.vo.bizIdentity.ZfObjectPageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/bizIdentity")
public class BizIdentityController {

    @Autowired
    private BizIdentityApplicationService bizIdentityApplicationService;

    /**
     * 查询所有主体
     */
    @PostMapping("/list")
    public Result list(@RequestBody ListBizIdentityDTO dto) {
        PageResult<GetAllBizIdentityVo> result = bizIdentityApplicationService.list(dto);
        return Result.ok(result);
    }

    /**
     * 根据类型或上级code查询业务主体
     */
    @GetMapping("/getByParam")
    public Result getByType(@RequestParam(value = "bizType") Integer bizType,
                            @RequestParam(value = "parentCode", required = false) String parentCode,
                            @RequestParam(value = "parentName", required = false) String parentName,
                            @RequestParam(value = "nameLike", required = false) String nameLike) {
        List<ZfObjectPageVO> voList = bizIdentityApplicationService.getByParam(bizType, parentCode, parentName, nameLike);
        return Result.ok(voList);
    }

    /**
     * 查询指定业务主体的专属页面是否创建
     */
    @GetMapping("/checkIsCreatedByPageCode")
    public Result checkIsCreatedByPageCode(@RequestParam("bizIdentityCode") String bizIdentityCode) {
        Map<String, Boolean> map = bizIdentityApplicationService.checkIsCreatedByPageCode(bizIdentityCode);
        return Result.ok(map);
    }
}
