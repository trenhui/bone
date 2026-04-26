package com.bone.lowcode.infra.interfaces.rest;

import com.bone.core.result.Result;
import com.bone.lowcode.infra.application.dto.page.UpdatePageDTO;
import com.bone.lowcode.infra.application.service.PageApplicationService;
import com.bone.lowcode.infra.application.vo.bizIdentity.BizIdentityVO;
import com.bone.lowcode.infra.application.vo.page.PageHeadVO;
import com.bone.lowcode.infra.application.vo.page.pageJson.NewPageVO;
import com.bone.lowcode.infra.infrastructure.common.annotation.HandleRepeatedReq;
import com.bone.lowcode.infra.infrastructure.common.annotation.KeyPart;
import com.pkh.cloud.auth.sdk.util.UserUtil;
import jakarta.validation.constraints.NotEmpty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

@RestController
@RequestMapping("/page")
@Validated
@Slf4j
public class PageController {

    @Autowired
    private PageApplicationService pageApplicationService;

    /**
     * 发布基础页面
     */
    @HandleRepeatedReq
    @GetMapping("/publishBasic")
    public Result publishBasic() {
        boolean flag = pageApplicationService.publishBasic();
        if (flag) return Result.ok("发布完成!");
        else return Result.error("发布失败!");
    }

    /**
     * 发布专属页面
     */
    @HandleRepeatedReq
    @GetMapping("/publishExclusive")
    public Result publishExclusivePage(@NotEmpty(message = "bizIdentityCode不能为空") @RequestParam("bizIdentityCode") String bizIdentityCode) {
        boolean flag = pageApplicationService.publishExclusive(bizIdentityCode);
        if (flag) return Result.ok("发布完成!");
        else return Result.error("发布失败!");
    }

    /**
     * 预览页面,基于编辑态数据
     */
    @GetMapping("/preview")
    public Result preview(@RequestParam("displayMode") String displayMode,
                          @RequestParam("pageCode") String pageCode,
                          @RequestParam(value = "bizIdentityCode", required = false) String bizIdentityCode) {
        NewPageVO pageVo = pageApplicationService.previewNew(displayMode, pageCode, bizIdentityCode);
        return Result.ok(pageVo);
    }

    /**
     * 预览页面,基于发布态数据
     */
    @GetMapping("/previewByRelease")
    public Result previewByRelease(@RequestParam("pageCode") String pageCode,
                                   @RequestParam(value = "bizIdentityCode", required = false) String bizIdentityCode,
                                   @RequestParam("displayMode") String displayMode) {
        NewPageVO pageVo = pageApplicationService.previewByRelease(pageCode, bizIdentityCode, displayMode);
        return Result.ok(pageVo);
    }

    /**
     * 创建专属页面
     */
    @HandleRepeatedReq
    @GetMapping("/createExclusivePage")
    public Result createBizPageNew(@RequestParam("bizType") Byte bizType,
                                   @RequestParam(value = "insuranceCompanyCode", required = false) String insuranceCompanyCode,
                                   @RequestParam(value = "insuranceCompanyName", required = false) String insuranceCompanyName,
                                   @RequestParam(value = "insuranceCompanyBranchCode", required = false) String insuranceCompanyBranchCode,
                                   @RequestParam(value = "insuranceCompanyBranchName", required = false) String insuranceCompanyBranchName,
                                   @RequestParam(value = "insuredCompanyCode", required = false) String insuredCompanyCode,
                                   @RequestParam(value = "insuredCompanyName", required = false) String insuredCompanyName,
                                   @RequestParam(value = "policyNo", required = false) String policyNo) {
        try {
            BizIdentityVO identityVO = pageApplicationService.createExclusivePage(bizType, insuranceCompanyCode, insuranceCompanyName,
                    insuranceCompanyBranchCode, insuranceCompanyBranchName, insuredCompanyCode, insuredCompanyName, policyNo);
            return Result.ok(identityVO);
        } catch (Exception e) {
            log.error("createExclusivePage error", e);
            return Result.error("创建失败,msg:" + e.getMessage());
        }
    }

    /**
     * 获取上次发布页面的时间
     */
    @GetMapping("/lastPublishTime")
    public Result lastPublishTime(@RequestParam(value = "bizIdentityCode", required = false) String bizIdentityCode) {
        Date time = pageApplicationService.lastPublishTime(bizIdentityCode);
        return Result.ok(time);
    }

    /**
     * 删除某主体的页面
     */
    @HandleRepeatedReq(true)
    @GetMapping("/delete")
    public Result delete(@KeyPart @RequestParam("bizIdentityCode") String bizIdentityCode) {
        Boolean flag = pageApplicationService.deleteBizIdentity(bizIdentityCode);
        if (flag) return Result.ok("删除完成!");
        else return Result.error("删除失败!");
    }

    /**
     * 拉取基础页面发布态中新增的的规则到指定主体下的页面编辑态
     */
    @HandleRepeatedReq(true)
    @GetMapping("/pullRulesFromBase")
    public Result pullRulesFromBase(@KeyPart @RequestParam("bizIdentityCode") String bizIdentityCode) {
        Boolean flag = pageApplicationService.pullRulesFromBase(bizIdentityCode);
        if (flag) return Result.ok("同步完成!");
        else return Result.error("同步失败!");
    }

    /**
     * 将某主体的录入页面复制到质检页面
     */
    @HandleRepeatedReq(true)
    @GetMapping("/copyEntryToQualityCheck")
    public Result copyEntryToQualityCheck(@KeyPart @RequestParam("bizIdentityCode") String identityCode) {
        try {
            pageApplicationService.copyEntryToQualityCheck(identityCode);
//            return Result.ok("功能暂停使用");
            return Result.ok("复制完成");
        } catch (Exception e) {
            log.error("复制录入页面到质检页面发生异常", e);
            return Result.error("复制失败,msg:" + e.getMessage());
        }
    }


    @Autowired
    private UserUtil userUtil;

    @GetMapping("/login")
    public Result login() {
        Boolean login = userUtil.isLogin();
        System.out.println("login = " + login);
        String userCode = userUtil.getUserCode();
        System.out.println("userCode = " + userCode);
        return Result.ok("login");
    }

    /**
     * 查询指定页面的元素id
     */
    @GetMapping("/getItems")
    public Object getItems(@RequestParam("pageCode") String pageCode,
                           @RequestParam(value = "bizIdentityCode", required = false) String identityCode) {
        return pageApplicationService.getItems(pageCode, identityCode);
    }

    /**
     * 获取page
     *
     * @param pageCode
     * @param bizIdentityCode
     * @return
     */
    @GetMapping("/getPage")
    public Result<PageHeadVO> getPage(@RequestParam("pageCode") String pageCode,
                                      @RequestParam(value = "bizIdentityCode") String bizIdentityCode) {
        PageHeadVO pageVo = pageApplicationService.getPage(pageCode, bizIdentityCode);
        return Result.ok(pageVo);
    }

    /**
     * 更新page
     *
     * @param pageDO
     * @return
     */
    @PostMapping("/updatePage")
    public Result<String> updatePage(@RequestBody UpdatePageDTO pageDO) {
        boolean flag = pageApplicationService.updatePage(pageDO);
        if (flag) {
            return Result.ok("更新成功!");
        } else {
            return Result.error("更新失败!");
        }
    }


}
