package com.bone.tpa.audit.adapter;


import com.bone.core.result.Result;
import com.bone.tpa.audit.application.InvoiceApplicationService;
import com.bone.tpa.audit.application.request.BatchUpdateAmountDTO;
import com.bone.tpa.audit.application.request.BatchUpdateDutyDTO;
import com.bone.tpa.audit.application.request.CopyInvoiceDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/invoice")
public class ClaimInvoiceController {

    @Autowired
    private InvoiceApplicationService invoiceApplicationService;

    @PostMapping("/duties/batchUpdate")
    public Result<String> batchUpdateDuties(@RequestBody BatchUpdateDutyDTO dto) {
        invoiceApplicationService.batchDutyUpdate(dto);
        return Result.ok("批量责任保存成功");
    }

    @PostMapping("/amount/batchUpdate")
    public Result<String> batchUpdateAmount(@RequestBody BatchUpdateAmountDTO dto) {
        invoiceApplicationService.batchUpdateAmount(dto);
        return Result.ok("批量拒赔保存成功");
    }

    @PostMapping("/copy")
    public Result<String> copy(@RequestBody CopyInvoiceDTO dto) {
        invoiceApplicationService.copy(dto);
        return Result.ok("保存成功");
    }
}
