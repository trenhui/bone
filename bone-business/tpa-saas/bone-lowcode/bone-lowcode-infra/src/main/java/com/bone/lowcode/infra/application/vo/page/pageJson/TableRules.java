package com.bone.lowcode.infra.application.vo.page.pageJson;

import com.bone.lowcode.infra.application.vo.table.AggregateRuleVO;
import com.bone.lowcode.infra.application.vo.tableRule.CrossTableDataEditVO;
import com.bone.lowcode.infra.application.vo.tableRule.CrossTableDataVerifyRuleVO;
import com.bone.lowcode.infra.application.vo.tableRule.TableRowEditRuleVO;
import com.bone.lowcode.infra.application.vo.tableRule.TableRowVerifyRuleVO;
import lombok.Data;

import java.util.List;

@Data
public class TableRules {

    /**
     * 分组聚合规则
     */
    List<AggregateRuleVO> groupAggregateRuleVOList;

    /**
     * 数据汇总规则
     */
    List<TableDataSummaryRule> summaryRuleList;

    /**
     * 行内编辑规则
     */
    List<TableRowEditRuleVO> rowEditRuleVOList;

    /**
     * 行内校验规则
     */
    List<TableRowVerifyRuleVO> rowVerifyRuleVOList;

    /**
     * 表间编辑规则
     */
    List<CrossTableDataEditVO> crossTableDataEditVOList;

    /**
     * 表间校验规则
     */
    List<CrossTableDataVerifyRuleVO> crossTableDataVerifyRuleVOList;
}
