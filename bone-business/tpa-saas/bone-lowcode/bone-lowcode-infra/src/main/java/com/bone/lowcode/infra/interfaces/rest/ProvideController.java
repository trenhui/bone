package com.bone.lowcode.infra.interfaces.rest;

import com.bone.core.result.Result;
import com.bone.lowcode.infra.application.dto.optionSet.QueryCollectionBindDTO;
import com.bone.lowcode.infra.application.dto.optionSet.QueryCollectionByOptionCnDTO;
import com.bone.lowcode.infra.application.dto.optionSet.QueryCollectionByOptionCodeDTO;
import com.bone.lowcode.infra.application.service.BizIdentityApplicationService;
import com.bone.lowcode.infra.application.service.OptionSetApplicationService;
import com.bone.lowcode.infra.application.vo.bizIdentity.BizIdentityVO;
import com.bone.lowcode.infra.application.vo.optionSet.QueryCollectionBindVO;
import com.bone.lowcode.infra.application.vo.optionSet.QueryCollectionByOptionCnVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/provide")
@Validated
public class ProvideController {

    @Autowired
    private BizIdentityApplicationService bizIdentityApplicationService;

    @Autowired
    private OptionSetApplicationService optionSetApplicationService;

    /**
     * 查询所有主体(给业务端使用)
     */
    @GetMapping("/getAllBizIdentity")
    public Result getAllBizIdentity() {
        List<BizIdentityVO> voList = bizIdentityApplicationService.getAllBizIdentity();
        return Result.ok(voList);
    }

    /**
     * 获取指定model下下拉框类型字段的数据源(给业务端使用)
     */
    @PostMapping("/queryCollectionBind")
    public Result queryCollectionBind(@Validated @RequestBody QueryCollectionBindDTO param) {
        List<QueryCollectionBindVO> list = optionSetApplicationService.queryCollectionBind(param);
        return Result.ok(list);
    }

    /**
     * 获取指定model下字段（SelectDrop类型）的数据源的值,通过选项值中文(给业务端使用)
     */
    @PostMapping("/queryCollectionByOptionCn")
    public Result queryCollectionByOptionCn(@Validated @RequestBody QueryCollectionByOptionCnDTO param) {
        QueryCollectionByOptionCnVO vo = optionSetApplicationService.queryCollectionByOptionCn(param);
        return Result.ok(vo);
    }

    /**
     * 获取指定model下字段（SelectDrop类型）的数据源的值,通过选项值code(给业务端使用)
     */
    @PostMapping("/queryCollectionByOptionCode")
    public Result queryCollectionByOptionCode(@Validated @RequestBody QueryCollectionByOptionCodeDTO param) {
        QueryCollectionByOptionCnVO vo = optionSetApplicationService.queryCollectionByOptionCode(param);
        return Result.ok(vo);
    }
}
