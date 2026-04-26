package com.bone.tpa.claim.adapter;

import com.bone.core.result.Result;
import com.bone.tpa.claim.application.SignRecordApplicationService;
import com.bone.tpa.claim.application.dto.SignRecordDTO;
import com.bone.tpa.claim.application.request.QueryListRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 签收控制器
 */
@RestController
@RequestMapping("/tpa/sign")
public class SignRecordController {
    @Autowired
    private SignRecordApplicationService signRecordApplicationService;

    /**
     * 创建签收记录
     */
    @PostMapping("/create")
    public Result<SignRecordDTO> createSignRecord(@RequestBody SignRecordDTO signRecordDTO) {
        SignRecordDTO signOffRecord = signRecordApplicationService.createSignRecord(signRecordDTO);
        return Result.ok(signOffRecord);
    }


    /**
     * 更新赔案相关人并且创建赔案
     */
    @GetMapping("/upload")
    public Result<Boolean> uploadParticipant(@RequestParam("id") Long id) {
        return Result.ok(signRecordApplicationService.uploadParticipant(id));
    }

    /**
     * 以下是查询接口
     *
     * 包括批量查询和单挑查询
     */
    @PostMapping("/all")
    public Result<List<SignRecordDTO>> getSignList(@RequestBody QueryListRequest request) {
        List<SignRecordDTO> signRecordDTOList = signRecordApplicationService.queryList(request);
        return Result.ok(signRecordDTOList);
    }


    @GetMapping("/detail")
    public Result<SignRecordDTO> getSignDetail(@RequestParam("id") Long id) {
        return Result.ok(signRecordApplicationService.getSignDetail(id));
    }


    /**
     * 确认完成签收
     */
    @GetMapping("/confirm")
    public Result<Boolean> confirmSign(@RequestParam("id") Long id) {
        signRecordApplicationService.confirmSign(id);
        return Result.ok(true);
    }


    /**
     * 初审通过接口
     * 通过前端按钮事件触发
     */
    @GetMapping("/pass")
    public Result<Boolean> passToNextStage(@RequestParam("id") Long claimId) {
        signRecordApplicationService.passToNextStage(claimId);
        return Result.ok(true);
    }

}
