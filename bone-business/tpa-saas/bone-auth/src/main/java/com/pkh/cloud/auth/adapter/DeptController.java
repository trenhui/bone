package com.pkh.cloud.auth.adapter;


import com.pkh.cloud.auth.application.enums.CommonStatusEnum;
import com.pkh.cloud.auth.application.vo.dept.DeptListReqVO;
import com.pkh.cloud.auth.application.vo.dept.DeptRespVO;
import com.pkh.cloud.auth.application.vo.dept.DeptSaveReqVO;
import com.pkh.cloud.auth.application.vo.dept.DeptSimpleRespVO;
import com.pkh.cloud.auth.domain.entity.dept.DeptDO;
import com.pkh.cloud.auth.domain.service.dept.DeptService;
import com.bone.core.result.Result;
import com.bone.core.util.BeanUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.bone.core.result.Result.success;


@Tag(name = "管理后台 - 部门")
@RestController
@RequestMapping("/system/dept")
@Validated
public class DeptController {

    @Resource
    private DeptService deptService;

    @PostMapping("create")
    @Operation(summary = "创建部门")
    //@PreAuthorize("@ss.hasPermission('system:dept:create')")
    public Result<Long> createDept(@Valid @RequestBody DeptSaveReqVO createReqVO) {
        Long deptId = deptService.createDept(createReqVO);
        return success(deptId);
    }

    @PutMapping("update")
    @Operation(summary = "更新部门")
    //@PreAuthorize("@ss.hasPermission('system:dept:update')")
    public Result<Boolean> updateDept(@Valid @RequestBody DeptSaveReqVO updateReqVO) {
        deptService.updateDept(updateReqVO);
        return success(true);
    }

    @DeleteMapping("delete")
    @Operation(summary = "删除部门")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    //@PreAuthorize("@ss.hasPermission('system:dept:delete')")
    public Result<Boolean> deleteDept(@RequestParam("id") Long id) {
        deptService.deleteDept(id);
        return success(true);
    }

    @GetMapping("/list")
    @Operation(summary = "获取部门列表")
    //@PreAuthorize("@ss.hasPermission('system:dept:query')")
    public Result<List<DeptRespVO>> getDeptList(DeptListReqVO reqVO) {
        List<DeptDO> list = deptService.getDeptList(reqVO);
        return success(BeanUtils.toBean(list, DeptRespVO.class));
    }

    @GetMapping(value = {"/list-all-simple", "/simple-list"})
    @Operation(summary = "获取部门精简信息列表", description = "只包含被开启的部门，主要用于前端的下拉选项")
    public Result<List<DeptSimpleRespVO>> getSimpleDeptList() {
        DeptListReqVO reqVO = new DeptListReqVO();
        reqVO.setStatus(CommonStatusEnum.ENABLE.getStatus());
        List<DeptDO> list = deptService.getDeptList(reqVO);
        return success(BeanUtils.toBean(list, DeptSimpleRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得部门信息")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    //@PreAuthorize("@ss.hasPermission('system:dept:query')")
    public Result<DeptRespVO> getDept(@RequestParam("id") Long id) {
        DeptDO dept = deptService.getDept(id);
        return success(BeanUtils.toBean(dept, DeptRespVO.class));
    }

}
