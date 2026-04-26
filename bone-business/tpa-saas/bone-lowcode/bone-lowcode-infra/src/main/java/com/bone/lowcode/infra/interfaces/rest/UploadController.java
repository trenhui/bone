package com.bone.lowcode.infra.interfaces.rest;

import com.bone.core.result.Result;
import com.bone.lowcode.infra.application.dto.upload.UpdateUploadAttachmentDTO;
import com.bone.lowcode.infra.application.dto.upload.UpdateUploadImageDTO;
import com.bone.lowcode.infra.application.dto.upload.UpdateUploadPictureDTO;
import com.bone.lowcode.infra.application.dto.upload.UploadDataDTO;
import com.bone.lowcode.infra.application.service.UploadApplicationService;
import com.bone.lowcode.infra.application.vo.upload.UploadAttachmentVO;
import com.bone.lowcode.infra.application.vo.upload.UploadDataVO;
import com.bone.lowcode.infra.application.vo.upload.UploadImageVO;
import com.bone.lowcode.infra.application.vo.upload.UploadPictureVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/upload")
@Validated
public class UploadController {

    @Autowired
    private UploadApplicationService uploadApplicationService;




    /**
     * 导入组件 查询(给业务端使用)
     */
    @GetMapping("/getUploadComponent")
    public Result getUploadComponent(@RequestParam("type") Byte type,
                                     @RequestParam("id") Long id) {
        Object re = uploadApplicationService.getUploadComponent(type, id);
        return Result.ok(re);
    }

    /**
     * 返回配置页面的导入组件
     */
    @GetMapping("/getUploadComponentList1")
    public Result getUploadComponentList1(@RequestParam(value = "bizIdentityCode", required = false) String bizIdentityCode) {
        List<Object> list = uploadApplicationService.getUploadComponentList1(bizIdentityCode);
        return Result.ok(list);
    }

    /**
     * 返回非配置页面的导入组件
     */
    @GetMapping("/getUploadComponentList2")
    public Result getUploadComponentList2(@RequestParam(value = "bizIdentityCode", required = false) String bizIdentityCode) {
        List<Object> list = uploadApplicationService.getUploadComponentList2(bizIdentityCode);
        return Result.ok(list);
    }

    /**
     * 导入数据组件 查询(通过code)
     */
    @GetMapping("/getUploadDataByCode")
    public Result getUploadDataByCode(@RequestParam("code") String code,
                                      @RequestParam(value = "bizIdentityCode", required = false) String bizIdentityCode) {
        UploadDataVO vo = uploadApplicationService.getUploadDataByCode(code, bizIdentityCode);
        return Result.ok(vo);
    }

    /**
     * 导入数据组件 查询(通过id)
     */
    @GetMapping("/getUploadDataById")
    public Result getUploadDataById(@RequestParam("id") Long id) {
        UploadDataVO vo = uploadApplicationService.getUploadDataById(id);
        return Result.ok(vo);
    }

    /**
     * 导入数据组件 更新
     */
    @PostMapping("/updateUploadData")
    public Result updateUploadData(@RequestBody UploadDataDTO dto) {
        boolean flag = uploadApplicationService.updateUploadData(dto);
        if (flag) return Result.ok("更新成功!");
        else return Result.error("更新失败!");
    }

    /**
     * 导入影像件组件 查询(通过code)
     */
    @GetMapping("/getUploadImageByCode")
    public Result getUploadImageByOwner(@RequestParam("code") String code,
                                        @RequestParam(value = "bizIdentityCode", required = false) String bizIdentityCode) {
        UploadImageVO vo = uploadApplicationService.getUploadImageByCode(code, bizIdentityCode);
        return Result.ok(vo);
    }

    /**
     * 导入影像件组件 查询(通过id)
     */
    @GetMapping("/getUploadImageById")
    public Result getUploadImageById(@RequestParam("id") Long id) {
        UploadImageVO vo = uploadApplicationService.getUploadImageById(id);
        return Result.ok(vo);
    }

    /**
     * 导入影像件组件 更新
     */
    @PostMapping("/updateUploadImage")
    public Result updateUploadImage(@RequestBody UpdateUploadImageDTO param) {
        boolean flag = uploadApplicationService.updateUploadImage(param);
        if (flag) return Result.ok("更新成功!");
        else return Result.error("更新失败!");
    }

    /**
     * 导入图片组件 查询(通过id)
     */
    @GetMapping("/getUploadPictureById")
    public Result getUploadPictureById(@RequestParam("id") Long id) {
        UploadPictureVO vo = uploadApplicationService.getUploadPictureById(id);
        return Result.ok(vo);
    }

    /**
     * 导入图片组件 查询(通过code)
     */
    @GetMapping("/getUploadPictureByCode")
    public Result getUploadPictureByCode(@RequestParam("code") String code,
                                         @RequestParam(value = "bizIdentityCode", required = false) String bizIdentityCode) {
        UploadPictureVO vo = uploadApplicationService.getUploadPictureByCode(code, bizIdentityCode);
        return Result.ok(vo);
    }

    /**
     * 导入图片组件 更新
     */
    @PostMapping("/updateUploadPicture")
    public Result updateUploadPicture(@RequestBody UpdateUploadPictureDTO param) {
        boolean flag = uploadApplicationService.updateUploadPicture(param);
        if (flag) return Result.ok("更新成功!");
        else return Result.error("更新失败!");
    }

    /**
     * 导入附件组件 查询(通过id)
     */
    @GetMapping("/getUploadAttachmentById")
    public Result getUploadAttachmentById(@RequestParam("id") Long id) {
        UploadAttachmentVO vo = uploadApplicationService.getUploadAttachmentById(id);
        return Result.ok(vo);
    }

    /**
     * 导入附件组件 更新
     */
    @PostMapping("/updateUploadAttachment")
    public Result updateUploadAttachment(@RequestBody UpdateUploadAttachmentDTO param) {
        boolean flag = uploadApplicationService.updateUploadAttachment(param);
        if (flag) return Result.ok("更新成功!");
        else return Result.error("更新失败!");
    }
}
