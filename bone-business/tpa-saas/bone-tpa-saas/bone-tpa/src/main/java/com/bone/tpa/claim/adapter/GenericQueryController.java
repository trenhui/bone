package com.bone.tpa.claim.adapter;

import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.bone.tpa.claim.application.GenericQueryApplicationService;
import com.bone.tpa.claim.application.request.DeleteRequest;
import com.bone.tpa.claim.application.request.QueryListRequest;
import com.bone.tpa.claim.application.request.QueryOneRequest;
import com.bone.tpa.claim.application.request.UpdateRequest;
import com.bone.tpa.claim.application.response.GenericQueryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通用搜索控制器
 */
@RestController
@RequestMapping("/tpa/query")
public class GenericQueryController {
    @Autowired
    private GenericQueryApplicationService genericQueryApplicationService;

    /**
     * 查询一条数据
     *
     * 提供该业务模型的数据的id
     */
    @PostMapping("/one")
    public Result<GenericQueryResponse> queryOne(@RequestBody QueryOneRequest request) {
        return Result.ok(genericQueryApplicationService.queryOne(request));
    }

    /**
     * 查询列表数据
     *
     * 提供该业务模型的父模型的id
     */
    @PostMapping("/list")
    public Result<PageResult<GenericQueryResponse>> queryList(@RequestBody QueryListRequest request) {
        return Result.ok(genericQueryApplicationService.queryList(request));
    }

    /**
     * 删除数据
     *
     * 可删除列表，使用id
     */
    @PostMapping("/delete")
    public Result<Boolean> deleteList(@RequestBody DeleteRequest request) {
        return Result.ok(genericQueryApplicationService.delete(request));
    }

    /**
     * 提交一条数据
     */
    @PostMapping("/update")
    public Result<Boolean> update(@RequestBody UpdateRequest request) {
        return Result.ok(genericQueryApplicationService.update(request));
    }

}
