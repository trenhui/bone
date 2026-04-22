package com.bone.blueprint.application.query.handler.dashboard;

import com.bone.metadata.sdk.query.QueryBuilder;
import com.bone.blueprint.application.query.qry.dashboard.DashboardPageQry;
import com.bone.blueprint.application.query.dto.dashboard.DashboardDTO;
import com.bone.core.result.PageResult;
import com.bone.blueprint.domain.model.dashboard.Dashboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DashboardPageQueryHandler {
    
    @Transactional(readOnly = true)
    public PageResult<DashboardDTO> handle(DashboardPageQry qry) {
        return QueryBuilder.from(Dashboard.class)
                .where("userId").eq(qry.getUserId())
                .orderBy("id", "desc")
                .page(qry.getPageNum(), qry.getPageSize())
                .mapTo(DashboardDTO.class);
    }
}
