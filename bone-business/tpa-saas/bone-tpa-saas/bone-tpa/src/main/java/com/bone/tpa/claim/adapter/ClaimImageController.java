package com.bone.tpa.claim.adapter;

import com.bone.core.result.Result;
import com.bone.tpa.claim.application.ClaimImageApplicationService;
import com.bone.tpa.claim.application.dto.ClaimImageDTO;
import com.bone.tpa.claim.application.dto.ImageTypeDTO;
import com.bone.tpa.claim.application.request.*;
import com.bone.tpa.claim.application.response.ImageVO;
import com.bone.tpa.claim.application.response.InvoiceBindResult;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;

/**
 * 影像件控制器
 */
@RestController
@RequestMapping("/tpa/image")
public class ClaimImageController {
    @Autowired
    private ClaimImageApplicationService claimImageApplicationService;

    /**
     * 查询对应赔案下的影像件
     */
    @PostMapping("/query")
    public Result<List<ClaimImageDTO>> getClaimImage(@RequestBody QueryListRequest request) {
        return Result.ok(claimImageApplicationService.getClaimImage(request));
    }

    /**
     * 将个人影像库下的影像添加到此赔案下面
     */
    @GetMapping("/addPersonalImageToClaim")
    public Result addPersonalImageToClaim(@RequestParam("claimNumber") Long claimNumber) {
        claimImageApplicationService.addPersonalImageToClaim(claimNumber);
        return Result.ok("添加完成");
    }

    /**
     * 查询该保险公司的影像件分类类别
     *
     * 调用新tpa
     */
    @GetMapping("/typeList")
    public Result<List<ImageTypeDTO>> getTypeList(@RequestParam("insuranceName") String insuranceName) {
        return Result.ok(claimImageApplicationService.getTypeList(insuranceName));
    }


    /**
     * 更新对应影像件的分类和清晰程度等
     */
    @PostMapping("/updatelist")
    public Result<Boolean> updateClaimImageList(@RequestBody ImageClassifyRequest request) {
        claimImageApplicationService.updateClaimImageList(request.getClaimImageDTOList());
        return Result.ok(true);
    }


    /**
     * 删除对应影像件
     */
    @PostMapping("/delete")
    public Result<Boolean> deleteClaimImages(@RequestBody DeleteRequest request) {
        claimImageApplicationService.deleteClaimImages(request);
        return Result.ok(true);
    }


    /**
     * 更新对应影像件的是否推送标记
     */
    @PostMapping("/push_flag")
    public Result<Boolean> updatePushFlag(@RequestBody ImageUpdateRequest request) {
        claimImageApplicationService.updatePushFlag(request);
        return Result.ok(true);
    }

    /**
     * 更新对应影像件的ocr标记
     *
     * 目前不使用
     */
    @PostMapping("/ocr_flag")
    public Result<Boolean> updateOcrFlag(@RequestBody ImageUpdateRequest request) {
        throw new TpaBizException(BizErrorCode.NOT_IMPLEMENTED);
//        claimImageApplicationService.updateOcrFlag(request);
//        return Result.ok(true);
    }


    /**
     * 查询能够用来绑定的发票列表
     */
    @GetMapping("/invoice_list")
    public Result<List<InvoiceBindResult>> invoiceListForImageBound(@RequestParam("id") Long imageId) {
        return Result.ok(claimImageApplicationService.invoiceListForImageBound(imageId));
    }


    /**
     * 发票绑定影像件
     *
     * 一张发票可以绑定多个影像件
     * 一个影像件可以绑定多个发票
     */
    @PostMapping("/bind_invoice")
    public Result<List<InvoiceBindResult>> imageBindInvoice(@RequestBody InvoiceBindRequest request) {
        return Result.ok(claimImageApplicationService.imageBindInvoice(request));
    }


    /**
     * 获取普康宝影像件
     *
     * 调用新tpa
     */
    @PostMapping("/pkbimage")
    public Result<Boolean> getPkbImage(@RequestBody GetPkbImageRequest request) {
        claimImageApplicationService.getPkbImage(request);
        return Result.ok();
    }
}
