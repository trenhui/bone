package com.bone.lowcode.infra.application.vo.tableRule;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FourTableRuleVO {

    //表格行内动态规则
    List<TableRowEditRuleVO> rowEditRuleList;

    //表间字段动态规则
    List<CrossTableDataEditVO> crossTableDataEditRuleList;

    //表格行内校验规则
    List<TableRowVerifyRuleVO> rowVerifyRuleList;

    //表间字段校验规则
    List<CrossTableDataVerifyRuleVO> crossTableDataVerifyRuleList;
}
