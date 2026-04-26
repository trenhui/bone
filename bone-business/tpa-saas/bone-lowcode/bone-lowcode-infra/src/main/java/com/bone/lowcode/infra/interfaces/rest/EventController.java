package com.bone.lowcode.infra.interfaces.rest;

import com.bone.core.result.Result;
import com.bone.lowcode.infra.application.dto.event.CreateEventDTO;
import com.bone.lowcode.infra.application.dto.event.CreateEventTriggerDTO;
import com.bone.lowcode.infra.application.dto.event.ListEventDTO;
import com.bone.lowcode.infra.application.dto.event.UpdateEventDTO;
import com.bone.lowcode.infra.application.service.EventApplicationService;
import com.bone.lowcode.infra.application.vo.event.GetEventVO;
import com.bone.lowcode.infra.application.vo.event.ListEventVO;
import com.bone.lowcode.infra.application.vo.event.PageEventVO;
import com.bone.lowcode.infra.application.vo.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/event")
public class EventController {

    @Autowired
    private EventApplicationService eventApplicationService;

    /**
     * 事件创建
     */
    @PostMapping("/create")
    public Result createEvent(@RequestBody CreateEventDTO dto) {
        boolean flag = eventApplicationService.createEvent(dto);
        return Result.ok(flag);
    }

    /**
     * 事件更新
     */
    @PostMapping("/update")
    public Result updateEvent(@RequestBody UpdateEventDTO dto) {
        boolean flag = eventApplicationService.updateEvent(dto);
        return Result.ok(flag);
    }

    /**
     * 事件列表
     */
    @PostMapping("/list")
    public Result listEvent(@RequestBody ListEventDTO dto) {
        PageResult<ListEventVO> result = eventApplicationService.listEvent(dto);
        return Result.ok(result);
    }

    /**
     * 事件详情
     */
    @GetMapping("/get")
    public Result getEvent(@RequestParam("eventId") Long eventId) {
        GetEventVO vo = eventApplicationService.getEvent(eventId);
        return Result.ok(vo);
    }

    /**
     * 创建事件触发器
     */
    @PostMapping("/createEventTrigger")
    public Result createEventTrigger(@RequestBody CreateEventTriggerDTO dto) {
        boolean flag = eventApplicationService.createEventTrigger(dto);
        return Result.ok(flag);
    }

    /**
     * 删除事件触发器
     */
    @GetMapping("/deleteEventTriggerById")
    public Result deleteEventTriggerById(@RequestParam("eventTriggerId") Long eventTriggerId) {
        boolean flag = eventApplicationService.deleteEventTriggerById(eventTriggerId);
        return Result.ok(flag);
    }

    /**
     * 返回页面的事件
     */
    @GetMapping("/getByPage")
    public Result getByPage(@RequestParam("pageCode") String pageCode,
                            @RequestParam(value = "bizIdentityCode", required = false) String bizIdentityCode,
                            @RequestParam(value = "type", required = false) Byte type) {
        List<PageEventVO> pageEventVOList = eventApplicationService.getByPage(pageCode, bizIdentityCode, type);
        return Result.ok(pageEventVOList);
    }
}
